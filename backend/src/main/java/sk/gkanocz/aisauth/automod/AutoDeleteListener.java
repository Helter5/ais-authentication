package sk.gkanocz.aisauth.automod;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.settings.AdminSettingsService;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Mirrors the old bot's handleAutoDelete: per-channel delayed message deletion with optional
 * user notification. Re-checks pinned status right before deleting since the delay window means
 * the message may have been pinned after it was posted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoDeleteListener extends ListenerAdapter {

    private final AutoDeleteConfigRepository autoDeleteConfigRepository;
    private final AdminSettingsService adminSettingsService;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild()) {
            return;
        }
        if (adminSettingsService.isMaintenanceMode()) {
            return;
        }
        String guildId = event.getGuild().getId();
        if (!adminSettingsService.get("autodelete_enabled_" + guildId, Boolean.class, false)) {
            return;
        }

        List<AutoDeleteConfig> configs =
                autoDeleteConfigRepository.findByGuildIdAndChannelId(guildId, event.getChannel().getId());
        if (configs.isEmpty()) {
            return;
        }
        AutoDeleteConfig config = configs.get(0);

        if (config.isIgnoreBots() && event.getAuthor().isBot()) {
            return;
        }
        if (readJson(config.getIgnoreUserIds()).contains(event.getAuthor().getId())) {
            return;
        }
        List<String> ignoreRoleIds = readJson(config.getIgnoreRoleIds());
        Member member = event.getMember();
        if (!ignoreRoleIds.isEmpty() && member != null
                && member.getRoles().stream().map(Role::getId).anyMatch(ignoreRoleIds::contains)) {
            return;
        }

        MessageChannel channel = event.getChannel().asGuildMessageChannel();
        String messageId = event.getMessageId();
        Runnable task = () -> performDelete(channel, messageId, config, event.getGuild().getName());
        if (config.getDelaySeconds() <= 0) {
            task.run();
        } else {
            scheduler.schedule(task, config.getDelaySeconds(), TimeUnit.SECONDS);
        }
    }

    private void performDelete(MessageChannel channel, String messageId, AutoDeleteConfig config, String guildName) {
        channel.retrieveMessageById(messageId).queue(
                message -> {
                    if (config.isIgnorePinned() && message.isPinned()) {
                        return;
                    }
                    message.delete().queue(
                            success -> notifyIfConfigured(channel, message, config, guildName),
                            failure -> log.debug("AutoDelete: failed to delete message {}", messageId));
                },
                failure -> { });
    }

    private void notifyIfConfigured(MessageChannel channel, Message message, AutoDeleteConfig config, String guildName) {
        if (!config.isNotifyUser()) {
            return;
        }
        String text = config.getNotifyMessage()
                .replace("{channel}", "#" + channel.getName())
                .replace("{server}", guildName)
                .replace("{user}", message.getAuthor().getName());

        if ("dm".equals(config.getNotifyVia())) {
            message.getAuthor().openPrivateChannel().queue(
                    dm -> dm.sendMessage(text).queue(s -> { }, f -> { }),
                    f -> { });
            return;
        }

        channel.sendMessage("<@" + message.getAuthor().getId() + "> " + text).queue(sent -> {
            if (config.isNotifyDeleteBotMsg()) {
                scheduler.schedule(
                        () -> sent.delete().queue(s -> { }, f -> { }),
                        Math.max(3, config.getNotifyDeleteDelay()), TimeUnit.SECONDS);
            }
        }, f -> { });
    }

    private List<String> readJson(String json) {
        return IgnoreListJson.read(objectMapper, json);
    }
}
