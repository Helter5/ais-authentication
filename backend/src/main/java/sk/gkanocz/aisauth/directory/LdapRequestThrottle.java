package sk.gkanocz.aisauth.directory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Java port of the old bot's global LDAP rateLimiter (1 request/sec, serialized queue) - protects
 * the real university LDAP server from being hammered by concurrent /verify calls. synchronized
 * gives the same "one at a time, spaced out" behavior the old app's promise queue did, without
 * needing a queue data structure: JDA dispatches each slash command on its own thread, so callers
 * naturally block here until it's their turn. Skipped in testing mode so repeated manual /verify
 * runs don't sit through the artificial delay.
 */
@Component
@RequiredArgsConstructor
class LdapRequestThrottle {

    private static final Duration MIN_INTERVAL = Duration.ofSeconds(1);

    private final VerificationProperties verificationProperties;

    private Instant lastRequestAt = Instant.EPOCH;

    synchronized void awaitTurn() {
        if (verificationProperties.testingMode()) {
            return;
        }
        Duration sinceLast = Duration.between(lastRequestAt, Instant.now());
        Duration remaining = MIN_INTERVAL.minus(sinceLast);
        if (!remaining.isNegative() && !remaining.isZero()) {
            try {
                Thread.sleep(remaining.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestAt = Instant.now();
    }
}
