package dev.openyourmouth.tts;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class VoicevoxCatalogServiceTest {
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/speakers", exchange -> VoicevoxProviderTest.send(exchange, 200, """
                [
                  {"name":"Zeta","styles":[{"name":"Normal","id":3}]},
                  {"name":"Alpha","styles":[{"name":"Happy","id":8},{"name":"Calm","id":7}]}
                ]
                """.getBytes(StandardCharsets.UTF_8)));
        server.start();
    }

    @AfterEach void stopServer() { server.stop(0); }

    @Test
    void flattensAndSortsCharacterStyles() {
        var styles = new VoicevoxCatalogService().fetch(endpoint()).join();
        assertEquals(3, styles.size());
        assertEquals("Alpha — Calm", styles.getFirst().name());
        assertEquals(7, styles.getFirst().id());
        assertEquals("Zeta — Normal", styles.getLast().name());
    }

    @Test
    void rejectsMalformedCatalog() {
        server.removeContext("/speakers");
        server.createContext("/speakers", exchange -> VoicevoxProviderTest.send(exchange, 200, "bad".getBytes(StandardCharsets.UTF_8)));
        assertThrows(java.util.concurrent.CompletionException.class, () -> new VoicevoxCatalogService().fetch(endpoint()).join());
    }

    private String endpoint() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
}
