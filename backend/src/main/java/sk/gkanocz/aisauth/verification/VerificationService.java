package sk.gkanocz.aisauth.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.gkanocz.aisauth.directory.StudentDirectoryService;
import sk.gkanocz.aisauth.directory.StudentRecord;
import sk.gkanocz.aisauth.directory.VerificationProperties;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final String CODE_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 15;
    private static final long CODE_VALIDITY_MINUTES = 15;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationCodeRepository verificationCodeRepository;
    private final VerifiedUserRepository verifiedUserRepository;
    private final StudentDirectoryService studentDirectoryService;
    private final VerificationProperties verificationProperties;

    @Transactional
    public VerificationCode initiateVerification(String discordId, String guildId, String aisId) {
        StudentRecord student = resolveEligibleStudent(discordId, guildId, aisId);

        // nahrádza INSERT OR REPLACE zo SQLite - zmažeme predchádzajúci pending kód, ak existuje
        verificationCodeRepository.deleteByDiscordIdAndGuildId(discordId, guildId);

        String code = generateCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(CODE_VALIDITY_MINUTES);
        VerificationCode verificationCode =
                new VerificationCode(discordId, guildId, code, student.mail(), aisId, expiresAt);
        return verificationCodeRepository.save(verificationCode);
    }

    /**
     * Read-only preview for the /verify confirmation embed (Discord button flow) - runs the same
     * LDAP lookup + eligibility checks as initiateVerification but writes nothing, so the user can
     * see which email the code would go to and back out before anything is actually sent. The
     * eligibility check re-runs in full when Confirm is clicked (initiateVerification again), which
     * also re-catches races (e.g. someone else claiming the AIS ID) between preview and confirm.
     */
    public String checkEligibility(String discordId, String guildId, String aisId) {
        return resolveEligibleStudent(discordId, guildId, aisId).mail();
    }

    private StudentRecord resolveEligibleStudent(String discordId, String guildId, String aisId) {
        assertValidAndNotAlreadyVerified(discordId, guildId, aisId);

        StudentRecord student = studentDirectoryService.findByAisId(aisId)
                .orElseThrow(() -> StudentNotFoundException.withAisId(aisId));

        if (!student.hasAccountStatus(verificationProperties.requiredAccountStatus())) {
            throw StudentNotEligibleException.notActiveStudent();
        }
        if (!student.belongsToAnyFaculty(verificationProperties.allowedFaculties())) {
            throw StudentNotEligibleException.wrongFaculty();
        }
        return student;
    }

    @Transactional
    public VerifiedUser confirmVerification(String discordId, String guildId, String inputCode) {
        VerificationCode pending = verificationCodeRepository
                .findByDiscordIdAndGuildIdAndExpiresAtAfter(discordId, guildId, LocalDateTime.now())
                .orElseThrow(InvalidVerificationCodeException::missingOrExpired);

        if (!pending.getCode().equals(inputCode)) {
            throw InvalidVerificationCodeException.wrongCode();
        }

        // znova skontrolovať race condition - niekto iný mohol medzitým overiť rovnaké AIS ID
        if (verifiedUserRepository.existsByAisIdAndGuildId(pending.getAisId(), guildId)) {
            verificationCodeRepository.deleteByDiscordIdAndGuildId(discordId, guildId);
            throw AlreadyVerifiedException.aisIdAlreadyVerified(pending.getAisId());
        }

        VerifiedUser verifiedUser = new VerifiedUser(pending.getAisId(), discordId, guildId, pending.getEmail());
        verifiedUserRepository.save(verifiedUser);
        verificationCodeRepository.deleteByDiscordIdAndGuildId(discordId, guildId);

        return verifiedUser;
    }

    @Transactional
    public VerifiedUser manuallyVerify(String discordId, String guildId, String aisId, String email) {
        assertValidAndNotAlreadyVerified(discordId, guildId, aisId);
        return verifiedUserRepository.save(new VerifiedUser(aisId, discordId, guildId, email));
    }

    public Optional<VerifiedUser> findVerifiedUser(String aisId, String guildId) {
        return verifiedUserRepository.findByAisIdAndGuildId(aisId, guildId);
    }

    /**
     * Rolls back a VerifiedUser row just created by confirmVerification/manuallyVerify when
     * assigning the Discord role afterward fails - mirrors the old bot's rollback in code.js /
     * ManualVerification.js. Without this, a failed role assignment left the user "verified" in
     * the database with no role and no way to retry /code (their pending code was already deleted).
     */
    @Transactional
    public void revertVerification(VerifiedUser verifiedUser) {
        verifiedUserRepository.delete(verifiedUser);
    }

    @Transactional
    public void cleanupDepartedMember(String discordId, String guildId) {
        verificationCodeRepository.deleteByDiscordIdAndGuildId(discordId, guildId);
        verifiedUserRepository.deleteByDiscordIdAndGuildId(discordId, guildId);
    }

    /**
     * The cheap, LDAP-free portion of eligibility (AIS ID format + already-verified checks against
     * the local DB) - callers that gate something expensive on eligibility (e.g. VerifyRateLimiter,
     * which shouldn't burn a rate-limit attempt on a request that was never going to reach LDAP)
     * should call this first and only proceed to {@link #checkEligibility} if it passes.
     */
    public void assertValidAndNotAlreadyVerified(String discordId, String guildId, String aisId) {
        if (!aisId.matches("\\d+")) {
            throw InvalidAisIdException.withValue(aisId);
        }
        if (verifiedUserRepository.existsByDiscordIdAndGuildId(discordId, guildId)) {
            throw AlreadyVerifiedException.discordUserAlreadyVerified(discordId);
        }
        if (verifiedUserRepository.existsByAisIdAndGuildId(aisId, guildId)) {
            throw AlreadyVerifiedException.aisIdAlreadyVerified(aisId);
        }
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
