package sk.gkanocz.aisauth.semester;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SemesterRoleMigrationRepository extends JpaRepository<SemesterRoleMigration, Long> {

    List<SemesterRoleMigration> findByGuildIdAndMigrationId(String guildId, String migrationId);

    List<SemesterRoleMigration> findByGuildIdAndMigrationIdAndRolledBackFalse(String guildId, String migrationId);

    long countByGuildIdAndMigrationId(String guildId, String migrationId);

    long countByGuildIdAndMigrationIdAndRolledBackTrue(String guildId, String migrationId);

    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
