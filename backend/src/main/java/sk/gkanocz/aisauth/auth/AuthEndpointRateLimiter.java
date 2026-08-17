package sk.gkanocz.aisauth.auth;

import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.shared.TooManyRequestsException;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Per-IP, in-memory throttle for the unauthenticated POST /api/auth/exchange and /api/auth/refresh
 * endpoints - they carry no @AuthenticationPrincipal at the point they run (that's their whole
 * purpose: exchange/refresh happen exactly when the caller doesn't have a valid access token yet),
 * so nothing else in SecurityConfig rate-limits them. Same in-memory-per-instance pattern and the
 * same "abuse guard, not a security boundary" trade-off as VerifyRateLimiter - the tokens these
 * endpoints consume are 256-bit SecureRandom values (brute force is infeasible regardless), this
 * exists to bound request-flooding/connection-pool exhaustion, not to stop credential guessing.
 */
@Component
class AuthEndpointRateLimiter {

    private static final int MAX_ATTEMPTS = 30;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final Map<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    void checkAndRecordAttempt(String endpoint, String clientIp) {
        Deque<Instant> attempts = attemptsByKey.computeIfAbsent(
                endpoint + ":" + clientIp, k -> new ConcurrentLinkedDeque<>());
        Instant now = Instant.now();
        attempts.removeIf(attempt -> Duration.between(attempt, now).compareTo(WINDOW) >= 0);

        if (attempts.size() >= MAX_ATTEMPTS) {
            throw TooManyRequestsException.withMessage("Too many requests. Please try again later.");
        }
        attempts.add(now);
    }
}
