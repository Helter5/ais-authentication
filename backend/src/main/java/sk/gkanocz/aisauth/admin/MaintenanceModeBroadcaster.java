package sk.gkanocz.aisauth.admin;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pushes maintenance-mode changes to every open dashboard tab instantly via SSE instead of making
 * clients poll and wait out a poll interval. One long-lived HTTP connection per open tab - fine at
 * this app's scale (a handful of concurrent managers), no WebSocket/broker needed for a single
 * one-directional boolean.
 */
@Component
public class MaintenanceModeBroadcaster {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(boolean currentlyEnabled) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("maintenance").data(Map.of("enabled", currentlyEnabled)));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void broadcast(boolean enabled) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("maintenance").data(Map.of("enabled", enabled)));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}
