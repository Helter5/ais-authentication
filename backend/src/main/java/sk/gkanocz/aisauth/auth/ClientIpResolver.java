package sk.gkanocz.aisauth.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Single place that reads the caller's IP from behind nginx (see infra/frontend/nginx.conf's
 * {@code proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;}) for both
 * {@link AuthEndpointRateLimiter} keys and audit-log entries ({@code AccessLog.ip}).
 *
 * <p>{@code $proxy_add_x_forwarded_for} *appends* the real connecting peer to whatever
 * {@code X-Forwarded-For} the client already sent, rather than replacing it - so for a request
 * carrying a client-supplied header, the value nginx forwards looks like
 * {@code "<attacker-chosen>, <real-ip>"}. There is exactly one trusted proxy hop in this
 * deployment (nginx - the backend has no host-port publish, see infra/docker-compose.yml), so the
 * *last* segment is the only one nginx itself vouches for; the first is attacker-controlled and
 * must never be trusted for rate limiting or logging (see security-audit-report.md MEDIUM-001 -
 * this class replaces two duplicate, first-segment-trusting copies that let the rate limiter be
 * bypassed by rotating the header value per request).
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] parts = forwardedFor.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
