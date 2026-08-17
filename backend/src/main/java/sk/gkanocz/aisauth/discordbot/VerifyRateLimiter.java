package sk.gkanocz.aisauth.discordbot;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Per-user /verify cooldown - Java port of the old bot's in-memory verifyAttempts Map (max 2
 * attempts per hour per user per guild). Purely in-memory like the original, so it resets on
 * restart; that's an acceptable trade-off for an abuse guard, not a security boundary.
 */
@Component
public class VerifyRateLimiter {

    private static final int MAX_ATTEMPTS = 2;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    /** Records this attempt and returns how many minutes until the caller may try again, or empty if allowed. */
    public Optional<Long> checkAndRecordAttempt(String discordId, String guildId) {
        Deque<Instant> attempts = attemptsByKey.computeIfAbsent(key(discordId, guildId), k -> new ConcurrentLinkedDeque<>());
        Instant now = Instant.now();
        attempts.removeIf(attempt -> Duration.between(attempt, now).compareTo(WINDOW) >= 0);

        if (attempts.size() >= MAX_ATTEMPTS) {
            Instant oldest = List.copyOf(attempts).get(0);
            long waitMinutes = (WINDOW.minus(Duration.between(oldest, now)).toSeconds() + 59) / 60;
            return Optional.of(Math.max(1, waitMinutes));
        }

        attempts.add(now);
        return Optional.empty();
    }

    private String key(String discordId, String guildId) {
        return guildId + ":" + discordId;
    }
}
