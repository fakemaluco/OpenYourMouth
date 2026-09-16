package dev.openyourmouth.tts;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

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

/** Loads character styles from a VOICEVOX-compatible engine. */
public final class VoicevoxCatalogService {
    private final HttpClient client;

    public VoicevoxCatalogService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build());
    }

    VoicevoxCatalogService(HttpClient client) {
        this.client = client;
    }

    public CompletableFuture<List<VoicevoxStyle>> fetch(String endpoint) {
        URI uri = VoicevoxProvider.resolve(VoicevoxProvider.endpoint(endpoint), "speakers", null);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
            if (response.statusCode() / 100 != 2) {
                throw new java.util.concurrent.CompletionException(new TtsHttpException(response.statusCode(),
                        "VOICEVOX speaker catalog failed (HTTP " + response.statusCode() + ")"));
            }
            return parse(response.body());
        });
    }

    static List<VoicevoxStyle> parse(byte[] bytes) {
        try {
            List<VoicevoxStyle> result = new ArrayList<>();
            for (JsonElement speakerElement : JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonArray()) {
                var speaker = speakerElement.getAsJsonObject();
                String speakerName = speaker.has("name") ? speaker.get("name").getAsString() : "VOICEVOX";
                if (!speaker.has("styles") || !speaker.get("styles").isJsonArray()) continue;
                for (JsonElement styleElement : speaker.getAsJsonArray("styles")) {
                    var style = styleElement.getAsJsonObject();
                    if (!style.has("id")) continue;
                    int id = style.get("id").getAsInt();
                    String styleName = style.has("name") ? style.get("name").getAsString() : Integer.toString(id);
                    result.add(new VoicevoxStyle(speakerName + " — " + styleName, id));
                }
            }
            result.sort(Comparator.comparing(VoicevoxStyle::name, String.CASE_INSENSITIVE_ORDER).thenComparingInt(VoicevoxStyle::id));
            return List.copyOf(result);
        } catch (Exception invalid) {
            throw new java.util.concurrent.CompletionException(new IllegalArgumentException("VOICEVOX returned an invalid speaker catalog"));
        }
    }
}
