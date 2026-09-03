package sk.gkanocz.aisauth.audit;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Best-effort audit trail for the dashboard's Logs page. Writes are done on a small background pool,
 * never on the caller's thread: an audit row is telemetry nobody is waiting on, and persisting it
 * inline meant a slow or unavailable Postgres stalled a Discord interaction handler for the full
 * ~10s HikariCP timeout - long past Discord's 3s deadline. A bounded queue with a discard policy
 * keeps a burst (or an outage) from growing unbounded; losing a few audit rows in that case is
 * acceptable, blocking user-facing commands is not.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 2, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(500),
            runnable -> {
                Thread thread = new Thread(runnable, "audit-log");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy());

    public void log(AuditLogEntry entry) {
        // Serialization is cheap and CPU-only - do it on the caller so a bad payload surfaces here,
        // then hand the actual DB write to the background pool.
        String detailsJson = entry.details() == null ? null : objectMapper.writeValueAsString(entry.details());
        executor.execute(() -> {
            try {
                auditLogRepository.save(new AuditLog(
                        entry.category(), entry.action(), entry.guildId(), entry.guildName(),
                        entry.channelId(), entry.channelName(), entry.userId(), entry.username(),
                        detailsJson));
            } catch (Exception e) {
                log.warn("Failed to persist audit log entry [{} / {}]: {}", entry.category(), entry.action(), e.getMessage());
            }
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
