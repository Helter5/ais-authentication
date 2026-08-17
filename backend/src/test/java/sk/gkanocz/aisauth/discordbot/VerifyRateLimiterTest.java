package sk.gkanocz.aisauth.discordbot;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VerifyRateLimiterTest {

    @Test
    void firstTwoAttemptsWithinTheWindowAreAllowed() {
        VerifyRateLimiter limiter = new VerifyRateLimiter();

        assertThat(limiter.checkAndRecordAttempt("discord-1", "guild-1")).isEmpty();
        assertThat(limiter.checkAndRecordAttempt("discord-1", "guild-1")).isEmpty();
    }

    @Test
    void thirdAttemptWithinTheWindowIsBlockedWithAWaitTime() {
        VerifyRateLimiter limiter = new VerifyRateLimiter();
        limiter.checkAndRecordAttempt("discord-1", "guild-1");
        limiter.checkAndRecordAttempt("discord-1", "guild-1");

        Optional<Long> wait = limiter.checkAndRecordAttempt("discord-1", "guild-1");

        assertThat(wait).isPresent();
        assertThat(wait.get()).isBetween(1L, 60L);
    }

    @Test
    void blockingAttemptIsNotItselfRecorded() {
        VerifyRateLimiter limiter = new VerifyRateLimiter();
        limiter.checkAndRecordAttempt("discord-1", "guild-1");
        limiter.checkAndRecordAttempt("discord-1", "guild-1");
        limiter.checkAndRecordAttempt("discord-1", "guild-1"); // blocked, 3rd call

        // still blocked, not "un-blocked" by the 3rd call having quietly incremented anything further
        assertThat(limiter.checkAndRecordAttempt("discord-1", "guild-1")).isPresent();
    }

    @Test
    void differentUsersHaveIndependentLimits() {
        VerifyRateLimiter limiter = new VerifyRateLimiter();
        limiter.checkAndRecordAttempt("discord-1", "guild-1");
        limiter.checkAndRecordAttempt("discord-1", "guild-1");

        assertThat(limiter.checkAndRecordAttempt("discord-2", "guild-1")).isEmpty();
    }

    @Test
    void sameUserDifferentGuildsHaveIndependentLimits() {
        VerifyRateLimiter limiter = new VerifyRateLimiter();
        limiter.checkAndRecordAttempt("discord-1", "guild-1");
        limiter.checkAndRecordAttempt("discord-1", "guild-1");

        assertThat(limiter.checkAndRecordAttempt("discord-1", "guild-2")).isEmpty();
    }
}
