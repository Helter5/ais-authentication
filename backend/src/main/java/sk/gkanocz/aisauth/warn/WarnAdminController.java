package sk.gkanocz.aisauth.warn;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.discordbot.DiscordNames;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class WarnAdminController {

    private final WarnService warnService;
    private final GuildAccessService guildAccessService;
    private final DiscordBotService discordBotService;

    @GetMapping("/warnings")
    public List<WarningResponse> getWarnings(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        Guild guild = discordBotService.jda().map(jda -> jda.getGuildById(guildId)).orElse(null);
        return warnService.getGuildWarns(guildId).stream()
                .map(warn -> WarningResponse.from(warn, guild))
                .toList();
    }

    public record WarningResponse(
            Long id,
            @JsonProperty("guild_id") String guildId,
            @JsonProperty("guild_name") String guildName,
            @JsonProperty("discord_id") String discordId,
            @JsonProperty("target_name") String targetName,
            @JsonProperty("moderator_id") String moderatorId,
            @JsonProperty("moderator_name") String moderatorName,
            String reason,
            @JsonProperty("created_at") LocalDateTime createdAt) {

        static WarningResponse from(Warn warn, Guild guild) {
            return new WarningResponse(
                    warn.getId(), warn.getGuildId(), guild == null ? null : guild.getName(),
                    warn.getDiscordId(), DiscordNames.memberName(guild, warn.getDiscordId()),
                    warn.getModeratorId(), DiscordNames.memberName(guild, warn.getModeratorId()),
                    warn.getReason(), warn.getCreatedAt());
        }
    }
}
