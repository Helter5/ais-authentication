package sk.gkanocz.aisauth.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void log(AuditLogEntry entry) {
        String detailsJson = entry.details() == null ? null : objectMapper.writeValueAsString(entry.details());
        auditLogRepository.save(new AuditLog(
                entry.category(), entry.action(), entry.guildId(), entry.guildName(),
                entry.channelId(), entry.channelName(), entry.userId(), entry.username(),
                detailsJson));
    }
}
