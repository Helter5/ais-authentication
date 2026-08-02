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

/**
 * Resolves a guild's configured log channel for an event type and posts an embed to it. Shared by
 * every feature (Hacked Account Trap, warn thresholds, ticket transcripts, wipe) that previously
 * reimplemented the same channel-lookup/null-check/try-catch boilerplate around LogRoutingService.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventLogEmbedSender {

    private final LogRoutingService logRoutingService;

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
