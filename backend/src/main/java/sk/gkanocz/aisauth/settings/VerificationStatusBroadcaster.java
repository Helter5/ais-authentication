package sk.gkanocz.aisauth.settings;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pushes verification-enabled changes to open dashboard tabs instantly via SSE, same pattern as
 * MaintenanceModeBroadcaster - except this is per-guild, not global, so each subscription is
 * tagged with the guildId it cares about and only receives events for that guild.
 */
@Component
public class VerificationStatusBroadcaster {

    private record Subscription(SseEmitter emitter, String guildId) {
    }

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(String guildId, boolean currentlyEnabled) {
        SseEmitter emitter = new SseEmitter(0L);
        Subscription subscription = new Subscription(emitter, guildId);
        subscriptions.add(subscription);
        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> subscriptions.remove(subscription));
        emitter.onError(e -> subscriptions.remove(subscription));

        try {
            emitter.send(SseEmitter.event().name("verification").data(Map.of("enabled", currentlyEnabled)));
        } catch (IOException e) {
            subscriptions.remove(subscription);
        }
        return emitter;
    }

    public void broadcast(String guildId, boolean enabled) {
        for (Subscription subscription : subscriptions) {
            if (!subscription.guildId().equals(guildId)) {
                continue;
            }
            try {
                subscription.emitter().send(SseEmitter.event().name("verification").data(Map.of("enabled", enabled)));
            } catch (IOException e) {
                subscriptions.remove(subscription);
            }
        }
    }
}
