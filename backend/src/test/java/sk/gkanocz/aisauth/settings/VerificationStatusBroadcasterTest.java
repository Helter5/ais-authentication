package sk.gkanocz.aisauth.settings;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * SseEmitter's send/initialize internals are package-private in Spring, so actual SSE payload
 * content isn't inspectable from here. The onCompletion/onTimeout/onError removal callbacks are
 * likewise untestable without a live servlet async request (Spring only invokes them from the
 * real AsyncListener lifecycle, not from calling emitter.complete() directly) - these tests cover
 * what's left: subscribing and broadcasting don't throw, for both matching and non-matching guilds.
 */
class VerificationStatusBroadcasterTest {

    private final VerificationStatusBroadcaster broadcaster = new VerificationStatusBroadcaster();

    @Test
    void subscribeReturnsAnEmitterWithoutThrowing() {
        SseEmitter emitter = broadcaster.subscribe("guild-1", true);

        assertThat(emitter).isNotNull();
    }

    @Test
    void broadcastWithNoSubscribersIsANoop() {
        assertThatCode(() -> broadcaster.broadcast("guild-1", true)).doesNotThrowAnyException();
    }

    @Test
    void broadcastToSubscribedGuildDoesNotThrow() {
        broadcaster.subscribe("guild-1", false);

        assertThatCode(() -> broadcaster.broadcast("guild-1", true)).doesNotThrowAnyException();
    }

    @Test
    void broadcastToADifferentGuildDoesNotThrow() {
        broadcaster.subscribe("guild-1", false);

        assertThatCode(() -> broadcaster.broadcast("guild-2", true)).doesNotThrowAnyException();
    }
}
