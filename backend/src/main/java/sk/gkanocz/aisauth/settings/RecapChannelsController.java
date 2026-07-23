package sk.gkanocz.aisauth.settings;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.audit.AuditLogEntry;
import sk.gkanocz.aisauth.audit.AuditLogService;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;

import java.util.Map;

@RestController
@RequestMapping("/api/recap-channels")
@RequiredArgsConstructor
public class RecapChannelsController {

    private final AdminSettingsService adminSettingsService;
    private final GuildAccessService guildAccessService;
    private final DiscordBotService discordBotService;
    private final AuditLogService auditLogService;

    @GetMapping
    public RecapChannelsResponse getRecapChannels(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        Guild guild = discordBotService.requireGuild(guildId);
        String wipeChannelId = channelId(guildId, "wipe");
        String semesterChannelId = channelId(guildId, "semester");
        return new RecapChannelsResponse(
                wipeChannelId, semesterChannelId,
                new RecapChannelsResponse.Health(health(guild, wipeChannelId), health(guild, semesterChannelId)));
    }

    @PostMapping
    public Map<String, Boolean> setRecapChannels(@AuthenticationPrincipal Claims claims, @RequestBody SetRecapChannelsRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        Guild guild = discordBotService.requireGuild(request.guildId());
        validate(guild, request.wipeChannelId());
        validate(guild, request.semesterChannelId());
        save(request.guildId(), "wipe", request.wipeChannelId());
        save(request.guildId(), "semester", request.semesterChannelId());
        logDashboardChange(claims, guild, request);
        return Map.of("success", true);
    }

    private void validate(Guild guild, String channelId) {
        if (channelId != null && !channelId.isBlank() && guild.getTextChannelById(channelId) == null) {
            throw InvalidRequestException.withMessage("Invalid log channel");
        }
    }

    private void save(String guildId, String type, String channelId) {
        String key = "recap_channel_" + type + "_" + guildId;
        if (channelId == null || channelId.isBlank()) {
            adminSettingsService.remove(key);
        } else {
            adminSettingsService.set(key, channelId);
        }
    }

    private String channelId(String guildId, String type) {
        return adminSettingsService.get("recap_channel_" + type + "_" + guildId, String.class, null);
    }

    private RecapChannelsResponse.HealthEntry health(Guild guild, String channelId) {
        if (channelId == null) {
            return new RecapChannelsResponse.HealthEntry(false, false, null);
        }
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            return new RecapChannelsResponse.HealthEntry(false, true, "Channel not found");
        }
        boolean canSend = guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_SEND);
        return new RecapChannelsResponse.HealthEntry(canSend, true, canSend ? null : "Missing Send Messages permission");
    }

    private void logDashboardChange(Claims claims, Guild guild, SetRecapChannelsRequest request) {
        try {
            auditLogService.log(new AuditLogEntry(
                    "dashboard", "Updated recap log channels", guild.getId(), guild.getName(),
                    null, null, claims.getSubject(), claims.get("username", String.class),
                    Map.of("wipeChannelId", String.valueOf(request.wipeChannelId()),
                            "semesterChannelId", String.valueOf(request.semesterChannelId()))));
        } catch (Exception e) {
            // best-effort audit trail; never block the underlying change on a logging failure
        }
    }

    public record SetRecapChannelsRequest(String guildId, String wipeChannelId, String semesterChannelId) {
    }

    public record RecapChannelsResponse(String wipeChannelId, String semesterChannelId, Health health) {

        public record Health(HealthEntry wipe, HealthEntry semester) {
        }

        public record HealthEntry(boolean ok, boolean configured, String error) {
        }
    }
}
