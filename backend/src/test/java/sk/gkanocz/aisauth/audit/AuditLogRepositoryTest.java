package sk.gkanocz.aisauth.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import sk.gkanocz.aisauth.TestcontainersConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestcontainersConfiguration.class)
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void findByCategoryAndGuildIdFiltersOutOtherCategoriesAndGuilds() {
        auditLogRepository.save(new AuditLog("commands", "/warn", "guild-1", "Guild One", null, null, "u1", "user1", null));
        auditLogRepository.save(new AuditLog("dashboard", "Updated setting", "guild-1", "Guild One", null, null, "u1", "user1", null));
        auditLogRepository.save(new AuditLog("commands", "/verify", "guild-2", "Guild Two", null, null, "u2", "user2", null));

        List<AuditLog> result = auditLogRepository.findByCategoryAndGuildIdOrderByCreatedAtDesc(
                "commands", "guild-1", PageRequest.of(0, 10));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAction()).isEqualTo("/warn");
    }

    @Test
    void findByCategoryAndGuildIdOrdersNewestFirst() throws InterruptedException {
        auditLogRepository.save(new AuditLog("commands", "/first", "guild-3", "G", null, null, "u", "user", null));
        Thread.sleep(10);
        auditLogRepository.save(new AuditLog("commands", "/second", "guild-3", "G", null, null, "u", "user", null));
        Thread.sleep(10);
        auditLogRepository.save(new AuditLog("commands", "/third", "guild-3", "G", null, null, "u", "user", null));

        List<AuditLog> result = auditLogRepository.findByCategoryAndGuildIdOrderByCreatedAtDesc(
                "commands", "guild-3", PageRequest.of(0, 10));

        assertThat(result).extracting(AuditLog::getAction).containsExactly("/third", "/second", "/first");
    }

    @Test
    void limitIsRespectedViaPageable() {
        for (int i = 0; i < 5; i++) {
            auditLogRepository.save(new AuditLog("commands", "/cmd" + i, "guild-4", "G", null, null, "u", "user", null));
        }

        List<AuditLog> result = auditLogRepository.findByCategoryAndGuildIdOrderByCreatedAtDesc(
                "commands", "guild-4", PageRequest.of(0, 2));

        assertThat(result).hasSize(2);
    }
}
