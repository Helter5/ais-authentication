package sk.gkanocz.aisauth.directory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LdapRequestThrottleTest {

    @Test
    void testingModeSkipsThrottlingEntirely() {
        VerificationProperties props = new VerificationProperties(List.of(), "ACTIVE", true);
        LdapRequestThrottle throttle = new LdapRequestThrottle(props);

        long start = System.nanoTime();
        throttle.awaitTurn();
        throttle.awaitTurn();
        throttle.awaitTurn();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(200);
    }

    @Test
    void firstCallDoesNotBlockWhenNotInTestingMode() {
        VerificationProperties props = new VerificationProperties(List.of(), "ACTIVE", false);
        LdapRequestThrottle throttle = new LdapRequestThrottle(props);

        long start = System.nanoTime();
        throttle.awaitTurn();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(200);
    }

    @Test
    void secondCallWithinTheSameSecondBlocksUntilTheIntervalElapses() {
        VerificationProperties props = new VerificationProperties(List.of(), "ACTIVE", false);
        LdapRequestThrottle throttle = new LdapRequestThrottle(props);

        long start = System.nanoTime();
        throttle.awaitTurn();
        throttle.awaitTurn();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(900);
    }
}
