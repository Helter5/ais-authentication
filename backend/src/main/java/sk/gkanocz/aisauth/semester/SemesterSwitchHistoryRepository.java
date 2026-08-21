package sk.gkanocz.aisauth.semester;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SemesterSwitchHistoryRepository extends JpaRepository<SemesterSwitchHistory, Long> {

    List<SemesterSwitchHistory> findByGuildIdOrderByCreatedAtDesc(String guildId);

    Optional<SemesterSwitchHistory> findByGuildIdAndMigrationId(String guildId, String migrationId);

    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
