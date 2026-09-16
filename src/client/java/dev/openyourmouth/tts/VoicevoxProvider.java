package dev.openyourmouth.tts;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.openyourmouth.audio.PcmAudio;
import dev.openyourmouth.config.TtsConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Client for the standard VOICEVOX Engine HTTP synthesis flow. */
public final class VoicevoxProvider implements TtsProvider {
    private final HttpClient client;

    public VoicevoxProvider() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build());
    }

    VoicevoxProvider(HttpClient client) {
        this.client = client;
    }

    @Override
    public short[] synthesize(String text, TtsConfig config) throws Exception {
        URI base = endpoint(config.voicevoxEndpoint);
        String query = "text=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                + "&speaker=" + config.voicevoxStyleId;
        HttpRequest queryRequest = HttpRequest.newBuilder(resolve(base, "audio_query", query))
                .timeout(Duration.ofSeconds(30)).POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<byte[]> queryResponse = client.send(queryRequest, HttpResponse.BodyHandlers.ofByteArray());
        requireSuccess("audio query", queryResponse);

        JsonObject audioQuery;
        try {
            audioQuery = JsonParser.parseString(new String(queryResponse.body(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception invalid) {
            throw new IllegalArgumentException("VOICEVOX returned an invalid audio query");
        }
        audioQuery.addProperty("speedScale", config.voicevoxSpeed);
        audioQuery.addProperty("pitchScale", config.voicevoxPitch);
        audioQuery.addProperty("intonationScale", config.voicevoxIntonation);
        audioQuery.addProperty("volumeScale", 1.0D);
        audioQuery.addProperty("outputSamplingRate", PcmAudio.SAMPLE_RATE);
        audioQuery.addProperty("outputStereo", false);

        HttpRequest synthesis = HttpRequest.newBuilder(resolve(base, "synthesis", "speaker=" + config.voicevoxStyleId))
                .timeout(Duration.ofSeconds(90))
                .header("Accept", "audio/wav")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(audioQuery.toString(), StandardCharsets.UTF_8)).build();
        HttpResponse<byte[]> response = client.send(synthesis, HttpResponse.BodyHandlers.ofByteArray());
        requireSuccess("synthesis", response);
        if (response.body().length == 0) throw new IllegalArgumentException("VOICEVOX returned empty audio");
        return PcmAudio.decodeLinearWav(response.body());
    }

    static URI endpoint(String value) {
        try {
            URI uri = URI.create(value.strip());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
            String path = uri.getPath();
            if (path == null || path.isBlank()) path = "/";
            if (!path.endsWith("/")) path += "/";
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), path, null, null);
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid VOICEVOX endpoint");
        }
    }

    static URI resolve(URI base, String operation, String query) {
        URI target = base.resolve(operation);
        try {
            return new URI(target.getScheme(), target.getUserInfo(), target.getHost(), target.getPort(), target.getPath(), query, null);
        } catch (Exception invalid) {
            throw new IllegalArgumentException("Invalid VOICEVOX request endpoint");
        }
    }

    public static boolean isLoopback(String value) {
        try {
            String host = endpoint(value).getHost();
            if ("localhost".equalsIgnoreCase(host) || "::1".equals(host)
                    || "0:0:0:0:0:0:0:1".equals(host)) return true;
            return host.matches("127(?:\\.\\d{1,3}){3}");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void requireSuccess(String operation, HttpResponse<byte[]> response) throws TtsHttpException {
        if (response.statusCode() / 100 == 2) return;
        throw new TtsHttpException(response.statusCode(), "VOICEVOX " + operation + " failed (HTTP " + response.statusCode() + ")");
    }
}
