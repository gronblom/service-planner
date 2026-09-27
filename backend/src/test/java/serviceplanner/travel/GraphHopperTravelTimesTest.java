package serviceplanner.travel;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import serviceplanner.model.GeoPoint;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against a fake GraphHopper that answers {@code /route} with canned JSON. */
class GraphHopperTravelTimesTest {
    private HttpServer server;
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String body = "{\"paths\":[{\"distance\":1234.5,\"time\":180000}]}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/route", exchange -> {
            lastQuery.set(exchange.getRequestURI().getQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private GraphHopperTravelTimes client() {
        return new GraphHopperTravelTimes("http://127.0.0.1:" + server.getAddress().getPort() + "/", "car");
    }

    @Test
    void readsTimeAndDistance() {
        Leg leg = client().between(new GeoPoint(52.37, 4.9), new GeoPoint(52.38, 4.91));

        assertEquals(180.0, leg.seconds());
        assertEquals(1234.5, leg.meters());
        assertTrue(lastQuery.get().contains("point=52.370000,4.900000&point=52.380000,4.910000"), lastQuery.get());
        assertTrue(lastQuery.get().contains("profile=car"), lastQuery.get());
    }

    @Test
    void reportsGraphHopperErrors() {
        status = 400;
        body = "{\"message\":\"Point 0 is out of bounds\"}";

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client().between(new GeoPoint(0, 0), new GeoPoint(52.38, 4.91)));
        assertTrue(error.getMessage().contains("out of bounds"), error.getMessage());
    }
}
