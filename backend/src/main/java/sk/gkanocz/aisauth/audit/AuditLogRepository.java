package sk.gkanocz.aisauth.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByCategoryOrderByCreatedAtDesc(String category, Pageable pageable);

    List<AuditLog> findByCategoryAndGuildIdOrderByCreatedAtDesc(String category, String guildId, Pageable pageable);
}
