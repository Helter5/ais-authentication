package sk.gkanocz.aisauth.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sk.gkanocz.aisauth.auth.AdminSessionRepository;
import sk.gkanocz.aisauth.auth.RefreshTokenRepository;
import sk.gkanocz.aisauth.directory.LdapConnectionSampleRepository;
import sk.gkanocz.aisauth.rolesnapshot.RoleSnapshotRepository;
import sk.gkanocz.aisauth.semester.SemesterRoleMigrationRepository;
import sk.gkanocz.aisauth.semester.SemesterSwitchHistoryRepository;
import sk.gkanocz.aisauth.semester.SemesterVisibilityMigrationRepository;
import sk.gkanocz.aisauth.verification.VerificationCodeRepository;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredDataCleanupJob {

    /** LDAP uptime probes run every 60s (see LdapUptimeProbeJob) - 30 days is ~43k rows, plenty for the dashboard's uptime graph without growing the table unbounded. */
    private static final int LDAP_SAMPLE_RETENTION_DAYS = 30;

    /** Switch/Setup history and its role/visibility migration rows - a rollback only ever targets a
     * recent run in practice, so 2 weeks keeps the tables bounded without losing anything anyone
     * would realistically still want to revert. */
    private static final int SEMESTER_HISTORY_RETENTION_DAYS = 14;

    private final VerificationCodeRepository verificationCodeRepository;
    private final AdminSessionRepository adminSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LdapConnectionSampleRepository ldapConnectionSampleRepository;
    private final RoleSnapshotRepository roleSnapshotRepository;
    private final SemesterSwitchHistoryRepository semesterSwitchHistoryRepository;
    private final SemesterRoleMigrationRepository semesterRoleMigrationRepository;
    private final SemesterVisibilityMigrationRepository semesterVisibilityMigrationRepository;

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        verificationCodeRepository.deleteByExpiresAtBefore(now);
        adminSessionRepository.deleteByExpiresAtBefore(now);
        refreshTokenRepository.deleteByExpiresAtBefore(now);
        ldapConnectionSampleRepository.deleteBySampledAtBefore(now.minusDays(LDAP_SAMPLE_RETENTION_DAYS));
        roleSnapshotRepository.deleteByExpiresAtBefore(now);
        LocalDateTime semesterHistoryCutoff = now.minusDays(SEMESTER_HISTORY_RETENTION_DAYS);
        semesterSwitchHistoryRepository.deleteByCreatedAtBefore(semesterHistoryCutoff);
        semesterRoleMigrationRepository.deleteByCreatedAtBefore(semesterHistoryCutoff);
        semesterVisibilityMigrationRepository.deleteByCreatedAtBefore(semesterHistoryCutoff);
        log.debug("Cleaned up expired verification codes, admin sessions, refresh tokens, old LDAP samples, "
                + "expired role snapshots, and semester switch history/migrations older than {} days",
                SEMESTER_HISTORY_RETENTION_DAYS);
    }
}
