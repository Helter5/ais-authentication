package sk.gkanocz.aisauth.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerificationFacade {

    private final VerificationService verificationService;
    private final VerificationEmailSender verificationEmailSender;

    public VerificationCode initiateAndNotify(String discordId, String guildId, String aisId) {
        VerificationCode verificationCode = verificationService.initiateVerification(discordId, guildId, aisId);
        verificationEmailSender.send(verificationCode.getEmail(), verificationCode.getCode());
        return verificationCode;
    }
}
