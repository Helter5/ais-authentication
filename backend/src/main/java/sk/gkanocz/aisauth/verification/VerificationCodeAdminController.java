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

    /**
     * Returns every code for the guild, active and expired alike - the dashboard's Codes page
     * filters/highlights by status client-side, which only works if expired codes actually reach
     * it instead of being excluded here.
     */
    @GetMapping("/verifications")
    public List<VerificationCodeResponse> getVerificationCodes(
            @AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return verificationCodeRepository
                .findByGuildIdOrderByCreatedAtDesc(guildId)
                .stream()
                .map(VerificationCodeResponse::from)
                .toList();
    }

    public record VerificationCodeResponse(
            Long id,
            @JsonProperty("discord_id") String discordId,
            @JsonProperty("guild_id") String guildId,
            String email,
            @JsonProperty("ais_id") String aisId,
            @JsonProperty("created_at") LocalDateTime createdAt,
            @JsonProperty("expires_at") LocalDateTime expiresAt) {

        static VerificationCodeResponse from(VerificationCode code) {
            return new VerificationCodeResponse(
                    code.getId(), code.getDiscordId(), code.getGuildId(),
                    code.getEmail(), code.getAisId(), code.getCreatedAt(), code.getExpiresAt());
        }
    }
}
