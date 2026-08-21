package sk.gkanocz.aisauth.semester;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SemesterVisibilityMigrationRepository extends JpaRepository<SemesterVisibilityMigration, Long> {

    List<SemesterVisibilityMigration> findByGuildIdAndMigrationId(String guildId, String migrationId);

    List<SemesterVisibilityMigration> findByGuildIdAndMigrationIdAndRolledBackFalse(String guildId, String migrationId);

    long countByGuildIdAndMigrationId(String guildId, String migrationId);

    long countByGuildIdAndMigrationIdAndRolledBackTrue(String guildId, String migrationId);

    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
