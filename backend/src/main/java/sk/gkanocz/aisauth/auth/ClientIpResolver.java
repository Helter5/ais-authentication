package sk.gkanocz.aisauth.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Single place that reads the caller's IP from behind the two-hop reverse-proxy chain in front of
 * the backend (see infra/Caddyfile's {@code reverse_proxy frontend:80} and
 * infra/../frontend/nginx.conf's {@code proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;})
 * for both {@link AuthEndpointRateLimiter} keys and audit-log entries ({@code AccessLog.ip}).
 *
 * <p>Caddy is the edge: it sees the real client socket and sets/appends {@code X-Forwarded-For}
 * with that address first. nginx sits behind it and appends *its own* upstream peer address (i.e.
 * Caddy's container IP) via {@code $proxy_add_x_forwarded_for} rather than replacing the header -
 * so the value reaching the backend looks like {@code "<attacker-chosen?>, <real client ip>,
 * <caddy container ip>"}. With two trusted hops (Caddy, then nginx - the backend has no
 * host-port publish, see infra/docker-compose.yml), the real client is always the
 * second-to-last segment, never the last (that's just Caddy's own container IP - see the
 * 172.29.0.x-in-production report this fixed) and never the first (attacker-controlled, see
 * security-audit-report.md MEDIUM-001 - this class replaces two duplicate, first-segment-trusting
 * copies that let the rate limiter be bypassed by rotating the header value per request).
 */
@Component
public class ClientIpResolver {

    /** Caddy (edge) + nginx (internal) - see class javadoc. */
    private static final int TRUSTED_PROXY_HOPS = 2;

    public String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] parts = forwardedFor.split(",");
            int index = Math.max(0, parts.length - TRUSTED_PROXY_HOPS);
            return parts[index].trim();
        }
        return request.getRemoteAddr();
    }
}
