package serviceplanner.travel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import serviceplanner.model.GeoPoint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Asks a self-hosted GraphHopper server (e.g. the Docker image) for one route per leg via
 * {@code GET /route}. Open-source GraphHopper has no matrix endpoint, so wrap this in
 * {@link CachingTravelTimes} to avoid asking for the same leg twice.
 */
public class GraphHopperTravelTimes implements TravelTimes {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();
    private final String baseUrl;
    private final String profile;

    public GraphHopperTravelTimes(String baseUrl, String profile) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.profile = profile;
    }

    @Override
    public Leg between(GeoPoint from, GeoPoint to) {
        URI uri = URI.create(String.format(Locale.ROOT,
                "%s/route?point=%f,%f&point=%f,%f&profile=%s&calc_points=false&instructions=false",
                baseUrl, from.lat(), from.lon(), to.lat(), to.lon(), profile));
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode body = json.readTree(response.body());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("GraphHopper returned " + response.statusCode()
                        + " for " + from + " -> " + to + ": " + body.path("message").asText(response.body()));
            }
            JsonNode path = body.path("paths").path(0);
            return new Leg(path.path("time").asDouble() / 1000.0, path.path("distance").asDouble());
        } catch (IOException e) {
            throw new UncheckedIOException("GraphHopper request failed: " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling GraphHopper", e);
        }
    }
}
