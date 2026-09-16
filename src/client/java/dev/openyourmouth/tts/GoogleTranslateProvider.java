package dev.openyourmouth.tts;

import dev.openyourmouth.config.TtsConfig;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.IOException;
import java.util.List;

public final class GoogleTranslateProvider implements TtsProvider {
    private static final URI ENDPOINT = URI.create("https://translate.google.com/translate_tts");
    private final HttpClient client;

    public GoogleTranslateProvider() {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    @Override
    public short[] synthesize(String text, TtsConfig config) throws Exception {
        List<String> chunks = TextChunker.split(text, 180);
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        for (int i = 0; i < chunks.size(); i++) {
            byte[] bytes = request(chunks.get(i), config.locale);
            short[] decoded = Mp3Decoder.decode(bytes);
            for (short sample : decoded) {
                result.write(sample & 0xFF);
                result.write((sample >>> 8) & 0xFF);
            }
            if (i + 1 < chunks.size()) result.write(new byte[PcmAudioSilence.BYTES_100_MS]);
        }
        return dev.openyourmouth.audio.PcmAudio.littleEndianToShorts(result.toByteArray());
    }

    private byte[] request(String text, String locale) throws Exception {
        String query = "ie=UTF-8&client=tw-ob&tl=" + encode(locale) + "&q=" + encode(text);
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + "?" + query))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 OpenYourMouth/1.0")
                .header("Accept", "audio/mpeg")
                .GET().build();
        HttpResponse<byte[]> response = sendWithRetry(request);
        if (response.statusCode() / 100 != 2) throw new TtsHttpException(response.statusCode(), "Google Translate TTS request failed");
        if (response.body().length == 0) throw new IllegalArgumentException("Google Translate returned an empty response");
        return response.body();
    }

    private HttpResponse<byte[]> sendWithRetry(HttpRequest request) throws Exception {
        HttpResponse<byte[]> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException transientFailure) {
            Thread.sleep(250L);
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }
        if (response.statusCode() == 429 || response.statusCode() >= 500) {
            Thread.sleep(250L);
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }
        return response;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static final class PcmAudioSilence {
        private static final int BYTES_100_MS = dev.openyourmouth.audio.PcmAudio.SAMPLE_RATE / 10 * 2;
    }
}
