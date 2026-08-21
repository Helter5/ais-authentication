package sk.gkanocz.aisauth.scheduling;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import sk.gkanocz.aisauth.TestcontainersConfiguration;
import sk.gkanocz.aisauth.auth.AdminSession;
import sk.gkanocz.aisauth.auth.AdminSessionRepository;
import sk.gkanocz.aisauth.directory.LdapConnectionSample;
import sk.gkanocz.aisauth.directory.LdapConnectionSampleRepository;
import sk.gkanocz.aisauth.semester.SemesterRoleMigration;
import sk.gkanocz.aisauth.semester.SemesterRoleMigrationRepository;
import sk.gkanocz.aisauth.semester.SemesterSwitchHistory;
import sk.gkanocz.aisauth.semester.SemesterSwitchHistoryRepository;
import sk.gkanocz.aisauth.semester.SemesterVisibilityMigration;
import sk.gkanocz.aisauth.semester.SemesterVisibilityMigrationRepository;
import sk.gkanocz.aisauth.verification.VerificationCode;
import sk.gkanocz.aisauth.verification.VerificationCodeRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for a real bug: cleanupExpired() called repository.deleteByExpiresAtBefore()
 * without @Transactional, which threw TransactionRequiredException the moment there was actually
 * a matching row to delete (silent no-op when the table was empty, which is why it went unnoticed
 * until expired rows had actually accumulated in a long-running container).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExpiredDataCleanupJobTest {

    @Autowired
    private ExpiredDataCleanupJob expiredDataCleanupJob;
    @Autowired
    private VerificationCodeRepository verificationCodeRepository;
    @Autowired
    private AdminSessionRepository adminSessionRepository;
    @Autowired
    private LdapConnectionSampleRepository ldapConnectionSampleRepository;
    @Autowired
    private SemesterSwitchHistoryRepository semesterSwitchHistoryRepository;
    @Autowired
    private SemesterRoleMigrationRepository semesterRoleMigrationRepository;
    @Autowired
    private SemesterVisibilityMigrationRepository semesterVisibilityMigrationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deletesExpiredVerificationCodesAndSessionsWithoutThrowing() {
        LocalDateTime past = LocalDateTime.now().minusMinutes(1);
        VerificationCode savedCode = verificationCodeRepository.save(new VerificationCode(
                "discord-1", "guild-1", "CODE123", "s@stuba.sk", "12345", past));
        adminSessionRepository.save(new AdminSession("jti-1", "discord-1", past));

        expiredDataCleanupJob.cleanupExpired();

        assertThat(verificationCodeRepository.findById(savedCode.getId())).isEmpty();
        assertThat(adminSessionRepository.findById("jti-1")).isEmpty();
    }

    @Test
    void doesNotDeleteStillValidRows() {
        LocalDateTime future = LocalDateTime.now().plusMinutes(15);
        verificationCodeRepository.save(new VerificationCode(
                "discord-2", "guild-1", "CODE456", "s2@stuba.sk", "67890", future));
        adminSessionRepository.save(new AdminSession("jti-2", "discord-2", future));

        expiredDataCleanupJob.cleanupExpired();

        assertThat(adminSessionRepository.findById("jti-2")).isPresent();
    }

    @Test
    void deletesLdapSamplesOlderThanRetentionWindowButKeepsRecentOnes() {
        // LdapConnectionSample always stamps sampledAt = now() on construction (it's a probe result,
        // not a value the caller controls) - backdating it here requires going around the entity.
        LdapConnectionSample old = ldapConnectionSampleRepository.save(new LdapConnectionSample(true, 42L, null));
        jdbcTemplate.update("UPDATE ldap_connection_samples SET sampled_at = ? WHERE id = ?",
                LocalDateTime.now().minusDays(31), old.getId());
        LdapConnectionSample recent = ldapConnectionSampleRepository.save(new LdapConnectionSample(false, null, "CommunicationException"));

        expiredDataCleanupJob.cleanupExpired();

        assertThat(ldapConnectionSampleRepository.findById(old.getId())).isEmpty();
        assertThat(ldapConnectionSampleRepository.findById(recent.getId())).isPresent();
    }

    @Test
    void deletesSemesterHistoryAndMigrationsOlderThan14DaysButKeepsRecentOnes() {
        LocalDateTime old = LocalDateTime.now().minusDays(15);
        LocalDateTime recent = LocalDateTime.now().minusDays(1);

        SemesterSwitchHistory oldHistory = semesterSwitchHistoryRepository.save(
                SemesterSwitchHistory.forSetup("guild-1", "migration-old", "1zs", "actor-1", "actor", old));
        SemesterSwitchHistory recentHistory = semesterSwitchHistoryRepository.save(
                SemesterSwitchHistory.forSetup("guild-1", "migration-recent", "1zs", "actor-1", "actor", recent));

        SemesterRoleMigration oldRoleRow = semesterRoleMigrationRepository.save(new SemesterRoleMigration(
                "guild-1", "migration-old", 0, "Setup 1zs", "discord-1", "role-from", null, false, old));
        SemesterRoleMigration recentRoleRow = semesterRoleMigrationRepository.save(new SemesterRoleMigration(
                "guild-1", "migration-recent", 0, "Setup 1zs", "discord-1", "role-from", null, false, recent));

        SemesterVisibilityMigration oldVisRow = semesterVisibilityMigrationRepository.save(new SemesterVisibilityMigration(
                "guild-1", "migration-old", 0, "Setup 1zs", "cat-1", "General", "show", true, false, old));
        SemesterVisibilityMigration recentVisRow = semesterVisibilityMigrationRepository.save(new SemesterVisibilityMigration(
                "guild-1", "migration-recent", 0, "Setup 1zs", "cat-1", "General", "show", true, false, recent));

        expiredDataCleanupJob.cleanupExpired();

        assertThat(semesterSwitchHistoryRepository.findById(oldHistory.getId())).isEmpty();
        assertThat(semesterSwitchHistoryRepository.findById(recentHistory.getId())).isPresent();
        assertThat(semesterRoleMigrationRepository.findById(oldRoleRow.getId())).isEmpty();
        assertThat(semesterRoleMigrationRepository.findById(recentRoleRow.getId())).isPresent();
        assertThat(semesterVisibilityMigrationRepository.findById(oldVisRow.getId())).isEmpty();
        assertThat(semesterVisibilityMigrationRepository.findById(recentVisRow.getId())).isPresent();
    }
}
