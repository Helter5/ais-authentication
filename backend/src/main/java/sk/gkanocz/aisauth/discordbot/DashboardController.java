package sk.gkanocz.aisauth.discordbot;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Guild;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.settings.GuildSettingsService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private static final int MAX_NICKNAME_LENGTH = 32;

    private final DiscordBotService discordBotService;
    private final GuildAccessService guildAccessService;
    private final GuildSettingsService guildSettingsService;

    @GetMapping("/dashboard")
    public DashboardResponse getDashboard(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        Guild guild = discordBotService.requireGuild(guildId);

        DashboardResponse.ServerInfo server = new DashboardResponse.ServerInfo(
                guild.getId(),
                guild.getName(),
                guild.getIconUrl(),
                guild.getMemberCount(),
                guild.getCategories().size(),
                guild.getTextChannels().size(),
                guild.getVoiceChannels().size(),
                guild.getRoles().size());

        String timezone = guildSettingsService.getOrCreate(guildId).getTimezone();
        DashboardResponse.Settings settings = new DashboardResponse.Settings(currentNickname(guild), timezone);
        DashboardResponse.Synchronization synchronization = new DashboardResponse.Synchronization(0, null, null);

        return new DashboardResponse(server, settings, synchronization);
    }

    @PostMapping("/dashboard/settings")
    public UpdateDashboardSettingsResponse updateDashboardSettings(
            @AuthenticationPrincipal Claims claims, @RequestBody UpdateDashboardSettingsRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        Guild guild = discordBotService.requireGuild(request.guildId());

        String nickname = normalizeNickname(request.nickname());
        guild.getSelfMember().modifyNickname(nickname).complete();
        guildSettingsService.updateField(request.guildId(), "timezone", request.timezone());

        String timezone = guildSettingsService.getOrCreate(request.guildId()).getTimezone();
        DashboardResponse.Settings settings = new DashboardResponse.Settings(currentNickname(guild), timezone);
        return new UpdateDashboardSettingsResponse(true, settings);
    }

    private String currentNickname(Guild guild) {
        return guild.getSelfMember().getNickname() != null
                ? guild.getSelfMember().getNickname()
                : guild.getJDA().getSelfUser().getName();
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return null;
        }
        String trimmed = nickname.trim();
        if (trimmed.length() > MAX_NICKNAME_LENGTH) {
            throw InvalidRequestException.withMessage("Nickname must be at most " + MAX_NICKNAME_LENGTH + " characters");
        }
        return trimmed;
    }

    public record UpdateDashboardSettingsRequest(String guildId, String nickname, String timezone) {
    }

    public record UpdateDashboardSettingsResponse(boolean success, DashboardResponse.Settings settings) {
    }
}
