package dev.openyourmouth.tts;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.openyourmouth.config.TtsConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FishVoiceCatalogServiceTest {
    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> response = new AtomicReference<>("""
            {"items":[
              {"_id":"b","title":"Zeta","type":"tts","state":"trained"},
              {"_id":"a","title":"Alpha","type":"tts","state":"trained"},
              {"_id":"skip-svc","title":"SVC","type":"svc","state":"trained"},
              {"_id":"skip-training","title":"Training","type":"tts","state":"training"}
            ]}
            """);
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private volatile boolean paged;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/model", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void fetchesOnlyOwnedTrainedTtsModelsAndSortsThem() {
        TtsConfig config = new TtsConfig();
        config.fishApiKey = "secret-key";

        var voices = service().fetchOwned(config).join();

        assertEquals(2, voices.size());
        assertEquals("Alpha", voices.getFirst().name);
        assertEquals("a", voices.getFirst().id);
        assertEquals(dev.openyourmouth.config.FishVoiceModel.Source.ACCOUNT, voices.getFirst().source);
        assertEquals("Bearer secret-key", authorization.get());
        assertTrue(query.get().contains("self=true"));
    }

    @Test
    void requiresApiKeyBeforeMakingRequest() {
        CompletionException error = assertThrows(CompletionException.class,
                () -> service().fetchOwned(new TtsConfig()).join());
        assertTrue(error.getCause().getMessage().contains("API key"));
    }

    @Test
    void reportsAuthenticationFailureWithoutLeakingKey() {
        status.set(401);
        TtsConfig config = new TtsConfig();
        config.fishApiKey = "never-show-this";

        CompletionException error = assertThrows(CompletionException.class,
                () -> service().fetchOwned(config).join());

        Throwable cause = unwrap(error);
        assertInstanceOf(TtsHttpException.class, cause);
        assertTrue(cause.getMessage().contains("rejected"));
        assertFalse(cause.getMessage().contains(config.fishApiKey));
    }

    @Test
    void rejectsInvalidCatalogJson() {
        response.set("not-json");
        TtsConfig config = new TtsConfig();
        config.fishApiKey = "key";
        assertThrows(CompletionException.class, () -> service().fetchOwned(config).join());
    }

    @Test
    void followsCatalogPagesUntilHasMoreIsFalse() {
        paged = true;
        TtsConfig config = new TtsConfig();
        config.fishApiKey = "key";

        var voices = service().fetchOwned(config).join();

        assertEquals(2, voices.size());
        assertEquals("First", voices.getFirst().name);
        assertEquals("Second", voices.get(1).name);
        assertEquals(2, requests.get());
    }

    @Test
    void distinguishesNoCreditFailure() {
        status.set(402);
        TtsConfig config = new TtsConfig();
        config.fishApiKey = "key";
        CompletionException error = assertThrows(CompletionException.class, () -> service().fetchOwned(config).join());
        assertTrue(unwrap(error).getMessage().contains("credit"));
    }

    private FishVoiceCatalogService service() {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/model?self=true&page_size=100&page_number=1");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        return new FishVoiceCatalogService(client, endpoint);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        query.set(exchange.getRequestURI().getQuery());
        String body = response.get();
        if (paged) {
            boolean secondPage = exchange.getRequestURI().getQuery().contains("page_number=2");
            body = secondPage
                    ? "{\"items\":[{\"_id\":\"second\",\"title\":\"Second\",\"type\":\"tts\",\"state\":\"trained\"}],\"has_more\":false}"
                    : "{\"items\":[{\"_id\":\"first\",\"title\":\"First\",\"type\":\"tts\",\"state\":\"trained\"}],\"has_more\":true}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status.get(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) current = current.getCause();
        return current;
    }
}
