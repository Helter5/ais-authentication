package sk.gkanocz.aisauth.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private final ObjectMapper objectMapper = new JsonMapper();
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository, objectMapper);
    }

    @Test
    void logSerializesDetailsToJsonBeforeSaving() {
        auditLogService.log(new AuditLogEntry(
                "dashboard", "Updated setting", "guild-1", "My Guild",
                "chan-1", "general", "user-1", "someone", Map.of("field", "value")));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, timeout(2000)).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getCategory()).isEqualTo("dashboard");
        assertThat(saved.getAction()).isEqualTo("Updated setting");
        assertThat(saved.getGuildId()).isEqualTo("guild-1");
        assertThat(saved.getDetails()).isEqualTo("{\"field\":\"value\"}");
    }

    @Test
    void logStoresNullDetailsWhenEntryHasNone() {
        auditLogService.log(new AuditLogEntry(
                "dashboard", "Ran job", "guild-1", "My Guild", null, null, "user-1", "someone", null));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, timeout(2000)).save(captor.capture());

        assertThat(captor.getValue().getDetails()).isNull();
    }
}
