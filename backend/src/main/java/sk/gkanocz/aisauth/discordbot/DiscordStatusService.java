package sk.gkanocz.aisauth.discordbot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Proxies discordstatus.com's public status API server-side instead of having the dashboard's own
 * browser fetch it directly - a browser extension/ad-blocker/corporate proxy silently blocking a
 * third-party statuspage.io domain would otherwise make the TopBar's Discord status dot never
 * appear, with no way for the admin to tell why. Cached briefly so every open dashboard tab polling
 * this doesn't hammer discordstatus.com on every request.
 */
@Slf4j
@Service
public class DiscordStatusService {

    private static final URI STATUS_URL = URI.create("https://discordstatus.com/api/v2/status.json");
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    private final ObjectMapper objectMapper;

    private volatile Result cached;
    private volatile Instant cachedAt = Instant.MIN;

    public DiscordStatusService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record Result(String indicator, String description) {
        static final Result UNKNOWN = new Result(null, null);
    }

    private record StatusResponse(StatusPayload status) {
    }

    private record StatusPayload(String indicator, String description) {
    }

    /** Null when discordstatus.com couldn't be reached and there's no still-fresh cached value either. */
    public Result status() {
        Result snapshot = cached;
        if (snapshot != null && Instant.now().isBefore(cachedAt.plus(CACHE_TTL))) {
            return snapshot;
        }
        return fetch();
    }

    private synchronized Result fetch() {
        // Re-check under the lock - another thread may have just refreshed it while this one waited.
        if (cached != null && Instant.now().isBefore(cachedAt.plus(CACHE_TTL))) {
            return cached;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(STATUS_URL).timeout(REQUEST_TIMEOUT).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            StatusPayload status = objectMapper.readValue(response.body(), StatusResponse.class).status();
            Result result = new Result(status.indicator(), status.description());
            cached = result;
            cachedAt = Instant.now();
            return result;
        } catch (Exception e) {
            log.debug("Could not fetch Discord API status: {}", e.getMessage());
            return cached != null ? cached : Result.UNKNOWN;
        }
    }
}
