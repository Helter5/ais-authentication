package sk.gkanocz.aisauth.semester;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pushes the tracked current semester type (Winter/Summer) to open dashboard tabs instantly via SSE
 * whenever it changes - a completed Plan run or a manual override (see
 * {@link SemesterOperationService#setCurrentPlan}) - same per-guild pattern as
 * {@link sk.gkanocz.aisauth.settings.VerificationStatusBroadcaster}.
 */
@Component
public class SemesterStatusBroadcaster {

    public record SemesterTypeEvent(String semesterType) {
    }

    private record Subscription(SseEmitter emitter, String guildId) {
    }

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(String guildId, String currentSemesterType) {
        SseEmitter emitter = new SseEmitter(0L);
        Subscription subscription = new Subscription(emitter, guildId);
        subscriptions.add(subscription);
        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> subscriptions.remove(subscription));
        emitter.onError(e -> subscriptions.remove(subscription));

        try {
            emitter.send(SseEmitter.event().name("semester").data(new SemesterTypeEvent(currentSemesterType)));
        } catch (IOException e) {
            subscriptions.remove(subscription);
        }
        return emitter;
    }

    public void broadcast(String guildId, String semesterType) {
        for (Subscription subscription : subscriptions) {
            if (!subscription.guildId().equals(guildId)) {
                continue;
            }
            try {
                subscription.emitter().send(SseEmitter.event().name("semester").data(new SemesterTypeEvent(semesterType)));
            } catch (IOException e) {
                subscriptions.remove(subscription);
            }
        }
    }
}
