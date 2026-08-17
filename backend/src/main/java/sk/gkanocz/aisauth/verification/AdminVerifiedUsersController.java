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
import sk.gkanocz.aisauth.auth.ManagerAccess;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AdminVerifiedUsersController {

    private final VerifiedUserRepository verifiedUserRepository;
    private final GuildAccessService guildAccessService;

    @ManagerAccess
    @GetMapping("/users")
    public List<VerifiedUserResponse> getUsers(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        return verifiedUserRepository.findByGuildId(guildId).stream()
                .map(VerifiedUserResponse::from)
                .toList();
    }

    public record VerifiedUserResponse(
            Long id,
            @JsonProperty("ais_id") String aisId,
            @JsonProperty("discord_id") String discordId,
            @JsonProperty("guild_id") String guildId,
            String email,
            @JsonProperty("verified_at") LocalDateTime verifiedAt) {

        static VerifiedUserResponse from(VerifiedUser user) {
            return new VerifiedUserResponse(
                    user.getId(), user.getAisId(), user.getDiscordId(),
                    user.getGuildId(), user.getEmail(), user.getVerifiedAt());
        }
    }
}
