package sk.gkanocz.aisauth.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.gkanocz.aisauth.directory.StudentDirectoryService;
import sk.gkanocz.aisauth.directory.StudentRecord;
import sk.gkanocz.aisauth.directory.VerificationProperties;

import java.security.SecureRandom;
import java.time.LocalDateTime;

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
        if (!aisId.matches("\\d+")) {
            throw InvalidAisIdException.withValue(aisId);
        }
        
        if (verifiedUserRepository.existsByDiscordIdAndGuildId(discordId, guildId)) {
            throw AlreadyVerifiedException.discordUserAlreadyVerified(discordId);
        }
        if (verifiedUserRepository.existsByAisIdAndGuildId(aisId, guildId)) {
            throw AlreadyVerifiedException.aisIdAlreadyVerified(aisId);
        }

        StudentRecord student = studentDirectoryService.findByAisId(aisId)
                .orElseThrow(() -> StudentNotFoundException.withAisId(aisId));

        if (!student.hasAccountStatus(verificationProperties.requiredAccountStatus())) {
            throw StudentNotEligibleException.notActiveStudent();
        }
        if (!student.belongsToAnyFaculty(verificationProperties.allowedFaculties())) {
            throw StudentNotEligibleException.wrongFaculty();
        }

        // nahrádza INSERT OR REPLACE zo SQLite - zmažeme predchádzajúci pending kód, ak existuje
        verificationCodeRepository.deleteByDiscordIdAndGuildId(discordId, guildId);

        String code = generateCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(CODE_VALIDITY_MINUTES);
        VerificationCode verificationCode =
                new VerificationCode(discordId, guildId, code, student.mail(), aisId, expiresAt);
        return verificationCodeRepository.save(verificationCode);
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

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
