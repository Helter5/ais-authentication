package sk.gkanocz.aisauth.verification;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.GuildAccessService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VerificationCodeAdminController {

    private final VerificationCodeRepository verificationCodeRepository;
    private final GuildAccessService guildAccessService;

    @GetMapping("/verifications")
    public List<PendingVerificationResponse> getPendingVerifications(
            @AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return verificationCodeRepository
                .findByGuildIdAndExpiresAtAfterOrderByCreatedAtDesc(guildId, LocalDateTime.now())
                .stream()
                .map(PendingVerificationResponse::from)
                .toList();
    }

    public record PendingVerificationResponse(
            Long id,
            @JsonProperty("discord_id") String discordId,
            @JsonProperty("guild_id") String guildId,
            String email,
            @JsonProperty("ais_id") String aisId,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("expires_at") LocalDateTime expiresAt) {

        static PendingVerificationResponse from(VerificationCode code) {
            return new PendingVerificationResponse(
                    code.getId(), code.getDiscordId(), code.getGuildId(),
                    code.getEmail(), code.getAisId(), code.getCreatedAt(), code.getExpiresAt());
        }
    }
}
