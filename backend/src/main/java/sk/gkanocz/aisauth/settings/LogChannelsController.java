package sk.gkanocz.aisauth.settings;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sk.gkanocz.aisauth.auth.GuildAccessService;
import sk.gkanocz.aisauth.auth.ManagerAccess;
import sk.gkanocz.aisauth.discordbot.DashboardAuditLogger;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;
import sk.gkanocz.aisauth.shared.InvalidRequestException;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/log-channels")
@RequiredArgsConstructor
public class LogChannelsController {

    private final LogRoutingService logRoutingService;
    private final GuildAccessService guildAccessService;
    private final DiscordBotService discordBotService;
    private final DashboardAuditLogger dashboardAuditLogger;

    @ManagerAccess
    @GetMapping
    public LogChannelsResponse getLogChannels(@AuthenticationPrincipal Claims claims, @RequestParam String guildId) {
        guildAccessService.assertCanManageGuild(claims, guildId);
        Guild guild = discordBotService.requireGuild(guildId);

        Map<LogEventType, String> current = new EnumMap<>(LogEventType.class);
        logRoutingService.listForGuild(guildId).forEach(sub -> current.put(sub.getEventType(), sub.getChannelId()));

        List<LogChannelsResponse.Entry> entries = Arrays.stream(LogEventType.values())
                .map(type -> toEntry(guild, type, current.get(type)))
                .toList();
        return new LogChannelsResponse(entries);
    }

    @ManagerAccess
    @PutMapping
    public Map<String, Boolean> updateLogChannels(
            @AuthenticationPrincipal Claims claims, @RequestBody UpdateLogChannelsRequest request) {
        guildAccessService.assertCanManageGuild(claims, request.guildId());
        Guild guild = discordBotService.requireGuild(request.guildId());

        Map<LogEventType, String> assignments = new EnumMap<>(LogEventType.class);
        request.assignments().forEach((eventTypeName, channelId) -> {
            LogEventType eventType = parseEventType(eventTypeName);
            if (channelId != null && !channelId.isBlank() && guild.getTextChannelById(channelId) == null) {
                throw InvalidRequestException.withMessage("Invalid log channel for " + eventType.label());
            }
            assignments.put(eventType, channelId);
        });

        logRoutingService.upsert(request.guildId(), assignments);
        dashboardAuditLogger.log(claims, guild, "Updated log channel routing", Map.of("assignments", request.assignments()));
        return Map.of("success", true);
    }

    private LogEventType parseEventType(String name) {
        try {
            return LogEventType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw InvalidRequestException.withMessage("Unknown event type: " + name);
        }
    }

    private LogChannelsResponse.Entry toEntry(Guild guild, LogEventType type, String channelId) {
        if (channelId == null) {
            return new LogChannelsResponse.Entry(type.name(), type.label(), type.description(), null, null, false, false, null);
        }
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            return new LogChannelsResponse.Entry(
                    type.name(), type.label(), type.description(), channelId, null, false, true, "Channel not found");
        }
        boolean canSend = guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_SEND);
        return new LogChannelsResponse.Entry(
                type.name(), type.label(), type.description(), channelId, channel.getName(),
                canSend, true, canSend ? null : "Missing Send Messages permission");
    }

    public record UpdateLogChannelsRequest(String guildId, Map<String, String> assignments) {
    }

    public record LogChannelsResponse(List<Entry> eventTypes) {

        public record Entry(
                String eventType,
                String label,
                String description,
                String channelId,
                String channelName,
                boolean ok,
                boolean configured,
                String error) {
        }
    }
}
