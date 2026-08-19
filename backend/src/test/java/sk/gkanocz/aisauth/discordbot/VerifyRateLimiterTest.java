package sk.gkanocz.aisauth.discordbot;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VerifyRateLimiterTest {

    @Test
    void firstFiveAttemptsWithinTheWindowAreAllowed() {
        VerifyRateLimiter limiter = new VerifyRateLimiter();

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.checkAndRecordAttempt("discord-1", "guild-1")).isEmpty();
        }
    }

    @Test
    void sixthAttemptWithinTheWindowIsBlockedWithAWaitTime() {
        VerifyRateLimiter limiter = new VerifyRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.checkAndRecordAttempt("discord-1", "guild-1");
        }

        Optional<Long> wait = limiter.checkAndRecordAttempt("discord-1", "guild-1");

        assertThat(wait).isPresent();
        assertThat(wait.get()).isBetween(1L, 60L);
    }

    @Test
    void blockingAttemptIsNotItselfRecorded() {
        VerifyRateLimiter limiter = new VerifyRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.checkAndRecordAttempt("discord-1", "guild-1");
        }
        limiter.checkAndRecordAttempt("discord-1", "guild-1"); // blocked, 6th call

        // still blocked, not "un-blocked" by the 6th call having quietly incremented anything further
        assertThat(limiter.checkAndRecordAttempt("discord-1", "guild-1")).isPresent();
    }

    @Test
    void differentUsersHaveIndependentLimits() {
        VerifyRateLimiter limiter = new VerifyRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.checkAndRecordAttempt("discord-1", "guild-1");
        }

        assertThat(limiter.checkAndRecordAttempt("discord-2", "guild-1")).isEmpty();
    }

    @Test
    void sameUserDifferentGuildsHaveIndependentLimits() {
        VerifyRateLimiter limiter = new VerifyRateLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.checkAndRecordAttempt("discord-1", "guild-1");
        }

        assertThat(limiter.checkAndRecordAttempt("discord-1", "guild-2")).isEmpty();
    }
}
