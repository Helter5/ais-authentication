package sk.gkanocz.aisauth.verification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/verification")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationFacade verificationFacade;
    private final VerificationService verificationService;

    @PostMapping
    public ResponseEntity<Void> initiateVerification(@Valid @RequestBody InitiateVerificationRequest request) {
        verificationFacade.initiateAndNotify(request.discordId(), request.guildId(), request.aisId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }


    @PostMapping("/confirm")
    public ResponseEntity<VerifiedUserResponse> confirmVerification(@Valid @RequestBody ConfirmVerificationRequest request) {
        VerifiedUser verifiedUser = verificationService.confirmVerification(
                request.discordId(), request.guildId(), request.code());
        return ResponseEntity.ok(VerifiedUserResponse.from(verifiedUser));
    }

    public record InitiateVerificationRequest(
            @NotBlank String discordId,
            @NotBlank String guildId,
            @NotBlank String aisId) {
    }


    public record ConfirmVerificationRequest(
            @NotBlank String discordId,
            @NotBlank String guildId,
            @NotBlank String code) {
    }

    public record VerifiedUserResponse(String aisId, String discordId, String guildId, String email) {
        static VerifiedUserResponse from(VerifiedUser user) {
            return new VerifiedUserResponse(user.getAisId(), user.getDiscordId(), user.getGuildId(), user.getEmail());
        }
    }
}
