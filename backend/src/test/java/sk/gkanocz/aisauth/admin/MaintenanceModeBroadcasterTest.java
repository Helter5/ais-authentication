package sk.gkanocz.aisauth.admin;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Same testability limits as VerificationStatusBroadcasterTest: SseEmitter's send/initialize
 * internals are package-private in Spring, and the onCompletion/onTimeout/onError removal callbacks
 * only fire from a real servlet AsyncListener lifecycle, not from calling emitter.complete()
 * directly - these tests cover what's observable: subscribing and broadcasting don't throw.
 */
class MaintenanceModeBroadcasterTest {

    private final MaintenanceModeBroadcaster broadcaster = new MaintenanceModeBroadcaster();

    @Test
    void subscribeReturnsAnEmitterWithoutThrowing() {
        SseEmitter emitter = broadcaster.subscribe(true);

        assertThat(emitter).isNotNull();
    }

    @Test
    void broadcastWithNoSubscribersIsANoop() {
        assertThatCode(() -> broadcaster.broadcast(true)).doesNotThrowAnyException();
    }

    @Test
    void broadcastAfterSubscribeDoesNotThrow() {
        broadcaster.subscribe(false);

        assertThatCode(() -> broadcaster.broadcast(true)).doesNotThrowAnyException();
    }
}
