package sk.gkanocz.aisauth.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import sk.gkanocz.aisauth.directory.VerificationProperties;

@Service
@RequiredArgsConstructor
public class VerificationFacade {

    private final VerificationService verificationService;
    private final VerificationEmailSender verificationEmailSender;
    private final VerificationProperties verificationProperties;

    public VerificationCode initiateAndNotify(String discordId, String guildId, String aisId) {
        VerificationCode verificationCode = verificationService.initiateVerification(discordId, guildId, aisId);
        if (!verificationProperties.testingMode()) {
            verificationEmailSender.send(verificationCode.getEmail(), verificationCode.getCode());
        }
        return verificationCode;
    }
}
