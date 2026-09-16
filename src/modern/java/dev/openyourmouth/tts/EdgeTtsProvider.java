package dev.openyourmouth.tts;

import dev.openyourmouth.audio.PcmAudio;
import dev.openyourmouth.config.TtsConfig;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Java implementation of the protocol used by rany2/edge-tts. */
public final class EdgeTtsProvider implements TtsProvider {
    private static final String WSS_PATH = "/consumer/speech/synthesize/readaloud/edge/v1";
    private static final DateTimeFormatter EDGE_DATE = DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US).withZone(ZoneOffset.UTC);
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public short[] synthesize(String text, TtsConfig config) throws Exception {
        String voice = EdgeVoiceCatalog.selectedOrDefault(config.locale, config.edgeVoice);
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        var chunks = TextChunker.split(clean(text), 950);
        for (int index = 0; index < chunks.size(); index++) {
            byte[] mp3 = requestWithRetry(chunks.get(index), voice, config.speakingRate, config.pitch);
            short[] decoded = Mp3Decoder.decode(mp3);
            for (short sample : decoded) {
                pcm.write(sample & 0xFF);
                pcm.write((sample >>> 8) & 0xFF);
            }
            if (index + 1 < chunks.size()) pcm.write(new byte[PcmAudio.SAMPLE_RATE / 10 * 2]);
        }
        return PcmAudio.littleEndianToShorts(pcm.toByteArray());
    }

    private byte[] requestWithRetry(String text, String voice, double speakingRate, double pitch) throws Exception {
        try {
            return request(text, voice, speakingRate, pitch, false);
        } catch (Exception first) {
            Thread.sleep(250L);
            // A fresh DNS key also avoids getting stuck on one unhealthy Edge anycast address.
            return request(text, voice, speakingRate, pitch, true);
        }
    }

    private byte[] request(String text, String voice, double speakingRate, double pitch, boolean alternateHost) throws Exception {
        String connectionId = EdgeVoiceCatalog.randomId().toLowerCase(Locale.ROOT);
        String gec = EdgeVoiceCatalog.generateGec(System.currentTimeMillis() / 1000L);
        String host = alternateHost ? "Speech.Platform.Bing.Com" : "speech.platform.bing.com";
        URI uri = URI.create("wss://" + host + WSS_PATH + "?TrustedClientToken=" + EdgeVoiceCatalog.TRUSTED_CLIENT_TOKEN + "&ConnectionId=" + connectionId +
                "&Sec-MS-GEC=" + gec + "&Sec-MS-GEC-Version=" + EdgeVoiceCatalog.GEC_VERSION);
        AudioListener listener = new AudioListener();
        WebSocket socket = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
                .header("User-Agent", EdgeVoiceCatalog.USER_AGENT)
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header("Pragma", "no-cache").header("Cache-Control", "no-cache")
                .header("Cookie", "muid=" + EdgeVoiceCatalog.randomId() + ";")
                .buildAsync(uri, listener).get(15, TimeUnit.SECONDS);
        try {
            String timestamp = EDGE_DATE.format(Instant.now());
            String config = "X-Timestamp:" + timestamp + "\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                    "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"}," +
                    "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n";
            socket.sendText(config, true).join();
            int rate = (int) Math.round((speakingRate - 1.0D) * 100.0D);
            int pitchHz = (int) Math.round(pitch);
            String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='" + xml(configLocale(voice)) + "'>" +
                    "<voice name='" + xml(voice) + "'><prosody pitch='" + signed(pitchHz) + "Hz' rate='" + signed(rate) + "%' volume='+0%'>" +
                    xml(text) + "</prosody></voice></speak>";
            String requestId = EdgeVoiceCatalog.randomId().toLowerCase(Locale.ROOT);
            String request = "X-RequestId:" + requestId + "\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:" + timestamp + "Z\r\nPath:ssml\r\n\r\n" + ssml;
            socket.sendText(request, true).join();
            byte[] audio = listener.audio().get(65, TimeUnit.SECONDS);
            if (audio.length == 0) throw new IllegalArgumentException("Edge TTS returned no audio");
            return audio;
        } finally {
            socket.abort();
        }
    }

    private static String clean(String text) {
        StringBuilder result = new StringBuilder(text.length());
        text.codePoints().forEach(code -> result.appendCodePoint((code <= 8 || code == 11 || code == 12 || (code >= 14 && code <= 31)) ? ' ' : code));
        return result.toString();
    }
    private static String xml(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;"); }
    private static String signed(int value) { return value >= 0 ? "+" + value : Integer.toString(value); }
    private static String configLocale(String voice) { int secondDash = voice.indexOf('-', 3); return secondDash > 0 ? voice.substring(0, secondDash) : "en-US"; }

    private static final class AudioListener implements WebSocket.Listener {
        private final CompletableFuture<byte[]> audio = new CompletableFuture<>();
        private final ByteArrayOutputStream result = new ByteArrayOutputStream();
        private final StringBuilder text = new StringBuilder();
        private final ByteArrayOutputStream binary = new ByteArrayOutputStream();

        CompletableFuture<byte[]> audio() { return audio; }
        @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                String message = text.toString();
                text.setLength(0);
                if (message.contains("Path:turn.end")) audio.complete(result.toByteArray());
            }
            webSocket.request(1);
            return null;
        }
        @Override public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            binary.writeBytes(bytes);
            if (last) {
                byte[] message = binary.toByteArray();
                binary.reset();
                if (message.length >= 2) {
                    int headerLength = ((message[0] & 0xFF) << 8) | (message[1] & 0xFF);
                    int audioStart = 2 + headerLength;
                    if (audioStart <= message.length) result.write(message, audioStart, message.length - audioStart);
                }
            }
            webSocket.request(1);
            return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (!audio.isDone()) audio.completeExceptionally(new IllegalStateException("Edge TTS connection closed: " + statusCode));
            return null;
        }
        @Override public void onError(WebSocket webSocket, Throwable error) { audio.completeExceptionally(error); }
    }
}
