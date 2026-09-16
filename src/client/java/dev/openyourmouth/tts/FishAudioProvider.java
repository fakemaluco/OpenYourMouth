package dev.openyourmouth.tts;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openyourmouth.audio.PcmAudio;
import dev.openyourmouth.config.TtsConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Fish Audio cloud and compatible self-hosted Fish Speech provider. */
public final class FishAudioProvider implements TtsProvider {
    private final HttpClient client;

    public FishAudioProvider() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    FishAudioProvider(HttpClient client) {
        this.client = client;
    }

    public static boolean needsApiKey(TtsConfig config) {
        try {
            return isOfficial(endpoint(config.fishEndpoint)) && config.fishApiKey.isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public short[] synthesize(String text, TtsConfig config) throws Exception {
        URI endpoint = endpoint(config.fishEndpoint);
        if (needsApiKey(config)) {
            throw new IllegalStateException("Fish Audio API key is not configured");
        }

        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        if (!config.fishReferenceId.isBlank()) body.addProperty("reference_id", config.fishReferenceId.strip());
        body.addProperty("temperature", config.fishTemperature);
        body.addProperty("top_p", config.fishTopP);
        body.addProperty("chunk_length", 300);
        body.addProperty("normalize", config.fishNormalize);
        body.addProperty("format", "wav");
        // Fish supports 48 kHz only for Opus; WAV/PCM tops out at 44.1 kHz.
        // PcmAudio.decodeLinearWav resamples the response to voice chat's 48 kHz.
        body.addProperty("sample_rate", 44_100);
        body.addProperty("latency", config.fishLatency);
        body.addProperty("repetition_penalty", 1.2D);
        body.addProperty("condition_on_previous_chunks", true);
        JsonObject prosody = new JsonObject();
        prosody.addProperty("speed", config.fishSpeed);
        prosody.addProperty("volume", 0.0D);
        prosody.addProperty("normalize_loudness", true);
        body.add("prosody", prosody);

        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(90))
                .header("Accept", "audio/wav")
                .header("Content-Type", "application/json")
                .header("model", config.fishModel)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (!config.fishApiKey.isBlank()) builder.header("Authorization", "Bearer " + config.fishApiKey.strip());

        HttpResponse<byte[]> response = sendWithRetry(builder.build());
        if (response.statusCode() / 100 != 2) {
            throw new TtsHttpException(response.statusCode(), errorFor(response.statusCode(), response.body(), config.fishApiKey));
        }
        if (response.body().length == 0) throw new IllegalArgumentException("Fish Audio returned an empty response");
        return PcmAudio.decodeLinearWav(response.body());
    }

    private HttpResponse<byte[]> sendWithRetry(HttpRequest request) throws Exception {
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 429 && response.statusCode() != 503 && response.statusCode() < 500) return response;
        } catch (IOException transientFailure) {
            // Retry once below without exposing the request or Authorization header.
        }
        Thread.sleep(300L);
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private static URI endpoint(String value) {
        URI uri;
        try {
            uri = URI.create(value.strip());
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid Fish TTS endpoint");
        }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
            throw new IllegalArgumentException("Invalid Fish TTS endpoint");
        }
        return uri;
    }

    private static boolean isOfficial(URI uri) {
        return "api.fish.audio".equalsIgnoreCase(uri.getHost());
    }

    private static String errorFor(int status, byte[] responseBody, String apiKey) {
        String summary = switch (status) {
            case 401, 403 -> "Fish Audio rejected the API key";
            case 402 -> "Fish Audio account has no available credit";
            case 429 -> "Fish Audio rate limit reached";
            case 503 -> "Fish Audio is temporarily overloaded";
            default -> "Fish Audio TTS request failed";
        };
        String detail = safeDetail(responseBody, apiKey);
        return detail.isBlank() ? summary : summary + ": " + detail;
    }

    private static String safeDetail(byte[] body, String apiKey) {
        if (body == null || body.length == 0 || body.length > 32_768) return "";
        try {
            var object = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            String detail = object.has("message") && object.get("message").isJsonPrimitive()
                    ? object.get("message").getAsString() : "";
            if (detail.isBlank() && object.has("reason") && object.get("reason").isJsonPrimitive()) detail = object.get("reason").getAsString();
            detail = detail.replace('\r', ' ').replace('\n', ' ').strip();
            if (!apiKey.isBlank()) detail = detail.replace(apiKey.strip(), "***");
            return detail.length() > 240 ? detail.substring(0, 240) : detail;
        } catch (Exception ignored) {
            return "";
        }
    }
}
