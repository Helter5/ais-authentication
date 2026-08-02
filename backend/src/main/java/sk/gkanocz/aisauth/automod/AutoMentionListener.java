package sk.gkanocz.aisauth.automod;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.settings.AdminSettingsService;

/**
 * Mentions the configured role whenever a message is posted in a channel with an enabled
 * auto-mention (dashboard: Modules -> Auto-Mentions).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoMentionListener extends ListenerAdapter {

    private final AutoMentionRepository autoMentionRepository;
    private final AdminSettingsService adminSettingsService;

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) {
            return;
        }
        String guildId = event.getGuild().getId();
        if (!adminSettingsService.get("automentions_enabled_" + guildId, Boolean.class, false)) {
            return;
        }
        String channelId = event.getChannel().getId();
        autoMentionRepository.findByGuildIdAndChannelId(guildId, channelId)
                .filter(AutoMention::isEnabled)
                .ifPresent(mention -> mention(event.getChannel().asGuildMessageChannel(), mention.getRoleId()));
    }

    private void mention(MessageChannel channel, String roleId) {
        channel.sendMessage("<@&" + roleId + ">").queue(
                success -> { },
                failure -> log.debug("AutoMention: failed to send mention in channel {}: {}", channel.getId(), failure.getMessage()));
    }
}
