package sk.gkanocz.aisauth.admin;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

    List<AccessLog> findByGuildIdOrderByCreatedAtDesc(String guildId, Pageable pageable);
}
