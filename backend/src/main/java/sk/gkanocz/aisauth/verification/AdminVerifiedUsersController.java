package sk.gkanocz.aisauth.verification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AdminVerifiedUsersController {

    private final VerifiedUserRepository verifiedUserRepository;

    @GetMapping("/users")
    public List<VerifiedUserResponse> getUsers(@RequestParam String guildId) {
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
