package sk.gkanocz.aisauth.wipe;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** Ephemeral per-run progress for a single guild's wipe: not configuration, so kept in memory only. */
class WipeState {

    volatile boolean running = true;
    volatile boolean done = false;
    final List<LogEntry> logs = new CopyOnWriteArrayList<>();
    final Stats stats;
    final String startedAt = Instant.now().toString();
    volatile String completedAt;

    WipeState(int total) {
        this.stats = new Stats(total);
    }

    void log(String msg, String level) {
        logs.add(new LogEntry(LocalTime.now().withNano(0).toString(), msg, level));
    }

    record LogEntry(String time, String msg, String level) {
    }

    static class Stats {
        final int total;
        final AtomicInteger processed = new AtomicInteger();
        final AtomicInteger inactive = new AtomicInteger();
        final AtomicInteger errors = new AtomicInteger();

        Stats(int total) {
            this.total = total;
        }
    }
}
