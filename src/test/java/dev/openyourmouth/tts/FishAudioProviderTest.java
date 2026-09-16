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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FishAudioProviderTest {
    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> model = new AtomicReference<>();
    private byte[] response = wav(960);

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tts", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsAdvancedSettingsAndDecodesWav() throws Exception {
        TtsConfig config = config();
        config.fishApiKey = "super-secret";
        config.fishModel = "s2.1-pro-free";
        config.fishReferenceId = "voice-id";
        config.fishLatency = "low";
        config.fishSpeed = 1.3D;
        config.fishTemperature = 0.4D;
        config.fishTopP = 0.8D;
        config.fishNormalize = false;

        short[] samples = new FishAudioProvider().synthesize("Olá Fish", config);

        assertEquals(960, samples.length);
        assertEquals("Bearer super-secret", authorization.get());
        assertEquals("s2.1-pro-free", model.get());
        var json = JsonParser.parseString(body.get()).getAsJsonObject();
        assertEquals("Olá Fish", json.get("text").getAsString());
        assertEquals("voice-id", json.get("reference_id").getAsString());
        assertEquals("low", json.get("latency").getAsString());
        assertEquals(44_100, json.get("sample_rate").getAsInt());
        assertEquals(1.3D, json.getAsJsonObject("prosody").get("speed").getAsDouble());
        assertFalse(json.get("normalize").getAsBoolean());
    }

    @Test
    void retriesOneTransientFailure() throws Exception {
        status.set(503);
        server.removeContext("/v1/tts");
        server.createContext("/v1/tts", exchange -> {
            if (requests.incrementAndGet() == 1) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
            } else {
                send(exchange, 200, response);
            }
        });
        assertEquals(960, new FishAudioProvider().synthesize("retry", config()).length);
        assertEquals(2, requests.get());
    }

    @Test
    void reportsPermanentHttpFailureWithoutResponseDetails() {
        status.set(402);
        TtsHttpException error = assertThrows(TtsHttpException.class, () -> new FishAudioProvider().synthesize("paid", config()));
        assertEquals(402, error.status());
        assertTrue(error.getMessage().contains("no available credit"));
    }

    @Test
    void exposesSafeApiErrorReasonAndRedactsKey() {
        status.set(400);
        response = "{\"message\":\"Invalid sample rate for super-secret\"}".getBytes(StandardCharsets.UTF_8);
        TtsConfig config = config();
        config.fishApiKey = "super-secret";
        TtsHttpException error = assertThrows(TtsHttpException.class, () -> new FishAudioProvider().synthesize("bad", config));
        assertTrue(error.getMessage().contains("Invalid sample rate"));
        assertFalse(error.getMessage().contains("super-secret"));
    }

    @Test
    void requiresKeyForOfficialCloudOnly() {
        TtsConfig config = new TtsConfig();
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new FishAudioProvider().synthesize("test", config));
        assertTrue(error.getMessage().contains("API key"));
    }

    @Test
    void rejectsInvalidAudio() {
        response = "not audio".getBytes(StandardCharsets.UTF_8);
        assertThrows(Exception.class, () -> new FishAudioProvider().synthesize("invalid", config()));
    }

    private TtsConfig config() {
        TtsConfig config = new TtsConfig();
        config.provider = TtsConfig.Provider.FISH_AUDIO;
        config.fishEndpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/tts";
        return config;
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        model.set(exchange.getRequestHeaders().getFirst("model"));
        send(exchange, status.get(), response);
    }

    private static void send(HttpExchange exchange, int code, byte[] bytes) throws IOException {
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static byte[] wav(int samples) {
        int dataLength = samples * 2;
        ByteArrayOutputStream output = new ByteArrayOutputStream(44 + dataLength);
        writeAscii(output, "RIFF");
        writeInt(output, 36 + dataLength);
        writeAscii(output, "WAVEfmt ");
        writeInt(output, 16);
        writeShort(output, 1);
        writeShort(output, 1);
        writeInt(output, 48_000);
        writeInt(output, 96_000);
        writeShort(output, 2);
        writeShort(output, 16);
        writeAscii(output, "data");
        writeInt(output, dataLength);
        output.writeBytes(new byte[dataLength]);
        return output.toByteArray();
    }

    private static void writeAscii(ByteArrayOutputStream output, String value) { output.writeBytes(value.getBytes(StandardCharsets.US_ASCII)); }
    private static void writeInt(ByteArrayOutputStream output, int value) { output.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()); }
    private static void writeShort(ByteArrayOutputStream output, int value) { output.writeBytes(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) value).array()); }
}
