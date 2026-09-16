package dev.openyourmouth.tts;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.openyourmouth.config.TtsConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class VoicevoxProviderTest {
    private HttpServer server;
    private final AtomicReference<String> audioQueryParameters = new AtomicReference<>();
    private final AtomicReference<String> synthesisBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/audio_query", exchange -> {
            audioQueryParameters.set(exchange.getRequestURI().getRawQuery());
            send(exchange, 200, """
                    {"accent_phrases":[],"speedScale":1.0,"pitchScale":0.0,"intonationScale":1.0,
                     "volumeScale":1.0,"prePhonemeLength":0.1,"postPhonemeLength":0.1,
                     "outputSamplingRate":24000,"outputStereo":false,"kana":""}
                    """.getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/synthesis", exchange -> {
            synthesisBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(exchange, 200, wav(960));
        });
        server.start();
    }

    @AfterEach void stopServer() { server.stop(0); }

    @Test
    void performsOfficialTwoStepFlowAndAppliesControls() throws Exception {
        TtsConfig config = new TtsConfig();
        config.provider = TtsConfig.Provider.VOICEVOX;
        config.voicevoxEndpoint = endpoint();
        config.voicevoxStyleId = 42;
        config.voicevoxSpeed = 1.35D;
        config.voicevoxPitch = -0.08D;
        config.voicevoxIntonation = 1.6D;

        short[] samples = new VoicevoxProvider().synthesize("Olá 世界", config);

        assertEquals(960, samples.length);
        assertTrue(audioQueryParameters.get().contains("speaker=42"));
        assertTrue(audioQueryParameters.get().contains("text="));
        var query = JsonParser.parseString(synthesisBody.get()).getAsJsonObject();
        assertEquals(1.35D, query.get("speedScale").getAsDouble());
        assertEquals(-0.08D, query.get("pitchScale").getAsDouble());
        assertEquals(1.6D, query.get("intonationScale").getAsDouble());
        assertEquals(48_000, query.get("outputSamplingRate").getAsInt());
        assertFalse(query.get("outputStereo").getAsBoolean());
    }

    @Test
    void rejectsInvalidAudioQueryJson() {
        server.removeContext("/audio_query");
        server.createContext("/audio_query", exchange -> send(exchange, 200, "invalid".getBytes(StandardCharsets.UTF_8)));
        TtsConfig config = new TtsConfig();
        config.voicevoxEndpoint = endpoint();
        assertThrows(IllegalArgumentException.class, () -> new VoicevoxProvider().synthesize("test", config));
    }

    @Test
    void classifiesLoopbackWithoutAcceptingArbitrarySchemes() {
        assertTrue(VoicevoxProvider.isLoopback("http://127.0.0.1:50021"));
        assertTrue(VoicevoxProvider.isLoopback("http://localhost:50021"));
        assertFalse(VoicevoxProvider.isLoopback("https://example.com"));
        assertThrows(IllegalArgumentException.class, () -> VoicevoxProvider.endpoint("file:///tmp/voicevox"));
    }

    private String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    static byte[] wav(int samples) {
        int dataLength = samples * 2;
        ByteArrayOutputStream output = new ByteArrayOutputStream(44 + dataLength);
        ascii(output, "RIFF"); integer(output, 36 + dataLength); ascii(output, "WAVEfmt "); integer(output, 16);
        shortValue(output, 1); shortValue(output, 1); integer(output, 48_000); integer(output, 96_000);
        shortValue(output, 2); shortValue(output, 16); ascii(output, "data"); integer(output, dataLength);
        output.writeBytes(new byte[dataLength]);
        return output.toByteArray();
    }
    private static void ascii(ByteArrayOutputStream output, String value) { output.writeBytes(value.getBytes(StandardCharsets.US_ASCII)); }
    private static void integer(ByteArrayOutputStream output, int value) { output.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()); }
    private static void shortValue(ByteArrayOutputStream output, int value) { output.writeBytes(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) value).array()); }
}
