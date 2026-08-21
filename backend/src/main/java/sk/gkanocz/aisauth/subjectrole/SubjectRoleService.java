package sk.gkanocz.aisauth.subjectrole;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.gkanocz.aisauth.settings.AdminSettingsService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tracks self-service /pridatpredmet subject-role requests per guild+user+semester, so a member can
 * grab their first couple of "repeated/extra subject" roles frictionlessly while anything past
 * that gets held for admin approval instead of silently handing out an unbounded number of roles.
 * The semester boundary is whatever SemesterPlanService.startPlan last reset it to - there is no
 * separate semester-epoch concept anywhere else in the app to hook into.
 */
@Service
@RequiredArgsConstructor
public class SubjectRoleService {

    /** First two subject-role requests per guild+user+semester auto-grant; the 3rd+ needs admin approval. */
    public static final int AUTO_GRANT_LIMIT = 2;

    private static final List<SubjectRoleRequestStatus> ACTIVE_STATUSES =
            List.of(SubjectRoleRequestStatus.GRANTED, SubjectRoleRequestStatus.APPROVED, SubjectRoleRequestStatus.PENDING);
    private static final LocalDateTime EPOCH = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final SubjectRoleRequestRepository subjectRoleRequestRepository;
    private final AdminSettingsService adminSettingsService;

    public LocalDateTime semesterResetAt(String guildId) {
        String stored = adminSettingsService.get("subjectrole_reset_" + guildId, String.class, null);
        if (stored == null) {
            return EPOCH;
        }
        try {
            return LocalDateTime.parse(stored);
        } catch (Exception e) {
            return EPOCH;
        }
    }

    /** Called from SemesterOperationService when a fresh (non-resume) semester switch starts. */
    @Transactional
    public void resetSemester(String guildId) {
        adminSettingsService.set("subjectrole_reset_" + guildId, LocalDateTime.now().toString());
    }

    public long activeCount(String guildId, String discordId, LocalDateTime since) {
        return subjectRoleRequestRepository.countByGuildIdAndDiscordIdAndStatusInAndCreatedAtAfter(
                guildId, discordId, ACTIVE_STATUSES, since);
    }

    @Transactional
    public SubjectRoleRequest recordGranted(String guildId, String discordId, String subjectCode) {
        return subjectRoleRequestRepository.save(
                new SubjectRoleRequest(guildId, discordId, subjectCode, SubjectRoleRequestStatus.GRANTED));
    }

    @Transactional
    public SubjectRoleRequest recordPending(String guildId, String discordId, String subjectCode) {
        return subjectRoleRequestRepository.save(
                new SubjectRoleRequest(guildId, discordId, subjectCode, SubjectRoleRequestStatus.PENDING));
    }

    @Transactional
    public SubjectRoleRequest recordMissing(String guildId, String discordId, String subjectCode) {
        return subjectRoleRequestRepository.save(
                new SubjectRoleRequest(guildId, discordId, subjectCode, SubjectRoleRequestStatus.MISSING_ROLE));
    }

    @Transactional
    public void decide(SubjectRoleRequest request, SubjectRoleRequestStatus status, String decidedBy) {
        request.decide(status, decidedBy);
        subjectRoleRequestRepository.save(request);
    }
}
