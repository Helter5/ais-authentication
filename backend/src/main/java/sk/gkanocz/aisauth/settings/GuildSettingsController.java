package sk.gkanocz.aisauth.settings;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.discordbot.GuildNotAvailableException;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GuildSettingsController {

    private final GuildSettingsService guildSettingsService;
    private final DiscordBotService discordBotService;

    @GetMapping("/settings")
    public GuildSettingsResponse getSettings(@RequestParam String guildId) {
        GuildSettings settings = guildSettingsService.getOrCreate(guildId);
        Guild guild = discordBotService.jda()
                .orElseThrow(GuildNotAvailableException::botNotConnected)
                .getGuildById(guildId);
        if (guild == null) {
            throw GuildNotAvailableException.guildNotFound(guildId);
        }

        GuildSettingsResponse.LogChannelHealth health = new GuildSettingsResponse.LogChannelHealth(
                checkChannelHealth(guild, settings.getLogChannelId()),
                checkChannelHealth(guild, settings.getWarnLogChannelId()),
                checkChannelHealth(guild, settings.getSpamLogChannelId()),
                checkChannelHealth(guild, settings.getTranscriptLogChannelId()));

        return new GuildSettingsResponse(
                settings.getVerifiedRoleId(),
                settings.getInactiveRoleId(),
                settings.getLogChannelId(),
                settings.getWarnLogChannelId(),
                settings.getSpamTrapChannelId(),
                settings.getSpamLogChannelId(),
                settings.getSpamDeleteInterval(),
                settings.isVerificationEnabled(),
                settings.getTranscriptLogChannelId(),
                health);
    }

    @PatchMapping("/settings")
    public Map<String, Boolean> updateSetting(@RequestBody UpdateSettingRequest request) {
        guildSettingsService.updateField(request.guildId(), request.field(), request.value());
        return Map.of("success", true);
    }

    private GuildSettingsResponse.LogChannelHealthEntry checkChannelHealth(Guild guild, String channelId) {
        if (channelId == null) {
            return new GuildSettingsResponse.LogChannelHealthEntry(false, false, null, null, null);
        }
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            return new GuildSettingsResponse.LogChannelHealthEntry(false, true, channelId, null, "Channel not found");
        }
        boolean canSend = guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_SEND);
        return new GuildSettingsResponse.LogChannelHealthEntry(
                canSend, true, channelId, channel.getName(), canSend ? null : "Missing Send Messages permission");
    }

    public record UpdateSettingRequest(String guildId, String field, Object value) {
    }
}
