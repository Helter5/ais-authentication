package sk.gkanocz.aisauth.discordbot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.settings.LogEventType;
import sk.gkanocz.aisauth.settings.LogRoutingService;

import java.awt.Color;
import java.time.Instant;

/**
 * Resolves a guild's configured log channel for an event type and posts an embed to it. Shared by
 * every feature (Hacked Account Trap, warn thresholds, wipe) that previously
 * reimplemented the same channel-lookup/null-check/try-catch boilerplate around LogRoutingService.
 *
 * Also the single place for this app's log-embed look: a shared color palette (SUCCESS/DANGER/
 * WARNING/NEUTRAL, meaning the same thing everywhere instead of nine slightly different hexes for
 * "bad") and a {@link #userField} helper so "User" fields render identically across every embed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventLogEmbedSender {

    public static final Color SUCCESS = new Color(0x22C55E);
    public static final Color DANGER = new Color(0xEF4444);
    public static final Color WARNING = new Color(0xF59E0B);
    public static final Color NEUTRAL = new Color(0x6366F1);

    private final LogRoutingService logRoutingService;

    /** Starting point for every log embed: color, title, one-line plain-language description, timestamp. */
    public static EmbedBuilder base(Color color, String title, String description) {
        EmbedBuilder embed = new EmbedBuilder().setColor(color).setTitle(title).setTimestamp(Instant.now());
        return description == null ? embed : embed.setDescription(description);
    }

    /** Standard "User" field value - mention plus username, same shape everywhere. */
    public static String userField(String discordId, String username) {
        return "<@" + discordId + "> (" + username + ")";
    }

    public TextChannel resolveChannel(Guild guild, LogEventType eventType) {
        String channelId = logRoutingService.channelIdFor(guild.getId(), eventType).orElse(null);
        return channelId == null ? null : guild.getTextChannelById(channelId);
    }

    public boolean send(Guild guild, LogEventType eventType, EmbedBuilder embed) {
        return send(guild, eventType, embed, null);
    }

    public boolean send(Guild guild, LogEventType eventType, EmbedBuilder embed, Button linkButton) {
        TextChannel channel = resolveChannel(guild, eventType);
        return channel != null && sendToChannel(channel, embed, linkButton);
    }

    /** For callers that already resolved the channel once and are sending several embeds to it (e.g. a batch). */
    public boolean sendToChannel(TextChannel channel, EmbedBuilder embed, Button linkButton) {
        try {
            MessageCreateAction action = channel.sendMessageEmbeds(embed.build());
            if (linkButton != null) {
                action = action.addActionRow(linkButton);
            }
            action.queue();
            return true;
        } catch (Exception e) {
            log.error("Failed to send log embed to channel {}: {}", channel.getId(), e.getMessage());
            return false;
        }
    }
}
