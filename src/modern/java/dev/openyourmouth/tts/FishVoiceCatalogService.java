package dev.openyourmouth.tts;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openyourmouth.config.FishVoiceModel;
import dev.openyourmouth.config.TtsConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads the authenticated user's trained TTS models from the official Fish Audio catalog. */
public final class FishVoiceCatalogService {
    private static final int MAX_PAGES = 5;
    private static final Pattern PAGE_NUMBER = Pattern.compile("([?&])page_number=\\d+");
    private static final URI OFFICIAL_MODELS = URI.create("https://api.fish.audio/model?self=true&page_size=100&page_number=1");
    private final HttpClient client;
    private final URI modelsEndpoint;

    public FishVoiceCatalogService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), OFFICIAL_MODELS);
    }

    FishVoiceCatalogService(HttpClient client, URI modelsEndpoint) {
        this.client = client;
        this.modelsEndpoint = modelsEndpoint;
    }

    public CompletableFuture<List<FishVoiceModel>> fetchOwned(TtsConfig config) {
        String apiKey = config.fishApiKey == null ? "" : config.fishApiKey.strip();
        if (apiKey.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Fish Audio API key is not configured"));
        }

        return fetchPage(apiKey, 1, new ArrayList<>()).thenApply(voices -> {
            voices.sort(Comparator.comparing(voice -> voice.name, String.CASE_INSENSITIVE_ORDER));
            return List.copyOf(voices);
        });
    }

    private CompletableFuture<List<FishVoiceModel>> fetchPage(String apiKey, int page, List<FishVoiceModel> collected) {
        HttpRequest request = HttpRequest.newBuilder(pageUri(page))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenCompose(response -> {
                    CatalogPage parsed = parseResponse(response.statusCode(), response.body());
                    collected.addAll(parsed.voices());
                    if (parsed.hasMore() && page < MAX_PAGES) return fetchPage(apiKey, page + 1, collected);
                    return CompletableFuture.completedFuture(collected);
                })
                .exceptionally(error -> {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                    if (cause instanceof RuntimeException runtime) throw runtime;
                    throw new CompletionException(cause);
                });
    }

    private URI pageUri(int page) {
        String value = modelsEndpoint.toString();
        Matcher matcher = PAGE_NUMBER.matcher(value);
        if (matcher.find()) return URI.create(matcher.replaceFirst("$1page_number=" + page));
        return URI.create(value + (value.contains("?") ? "&" : "?") + "page_number=" + page);
    }

    private static CatalogPage parseResponse(int status, String body) {
        if (status / 100 != 2) {
            String message = switch (status) {
                case 401, 403 -> "Fish Audio rejected the API key";
                case 402 -> "Fish Audio account has no available credit";
                case 429 -> "Fish Audio rate limit reached";
                case 500, 502, 503, 504 -> "Fish Audio is temporarily unavailable";
                default -> "Fish Audio model catalog request failed";
            };
            throw new CompletionException(new TtsHttpException(status, message));
        }

        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!root.has("items") || !root.get("items").isJsonArray()) {
                throw new IllegalArgumentException("Fish Audio returned an invalid model catalog");
            }
            List<FishVoiceModel> voices = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("items")) {
                if (!element.isJsonObject()) continue;
                JsonObject item = element.getAsJsonObject();
                String id = string(item, "_id");
                String title = string(item, "title");
                String type = string(item, "type");
                String state = string(item, "state");
                if (id.isBlank() || (!type.isBlank() && !"tts".equalsIgnoreCase(type))) continue;
                if (!state.isBlank() && !"trained".equalsIgnoreCase(state)) continue;
                voices.add(new FishVoiceModel(title.isBlank() ? id : title, id, FishVoiceModel.Source.ACCOUNT));
            }
            boolean hasMore = root.has("has_more") && root.get("has_more").isJsonPrimitive() && root.get("has_more").getAsBoolean();
            return new CatalogPage(voices, hasMore);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Fish Audio returned an invalid model catalog", exception);
        }
    }

    private static String string(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return "";
        try {
            return object.get(key).getAsString().strip();
        } catch (Exception ignored) {
            return "";
        }
    }

    private record CatalogPage(List<FishVoiceModel> voices, boolean hasMore) {}
}
