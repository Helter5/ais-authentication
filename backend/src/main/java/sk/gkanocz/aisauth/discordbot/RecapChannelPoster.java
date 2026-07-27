package sk.gkanocz.aisauth.discordbot;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Posts a summary message with a log-file attachment to a configured recap channel. Shared by
 * every long-running admin operation (wipe, semester setup/switch, ...) that reports its run log
 * back to a Discord channel, instead of each one re-implementing the same channel lookup and
 * FileUpload plumbing.
 */
@Slf4j
@Component
public class RecapChannelPoster {

    public void post(Guild guild, String channelId, String content, List<String> logLines, String filename) {
        if (channelId == null) {
            return;
        }
        try {
            TextChannel channel = guild.getTextChannelById(channelId);
            if (channel == null) {
                return;
            }
            String logText = String.join("\n", logLines);
            channel.sendMessage(content)
                    .addFiles(FileUpload.fromData(logText.getBytes(StandardCharsets.UTF_8), filename))
                    .queue();
        } catch (Exception e) {
            log.error("Failed to post recap to channel {}: {}", channelId, e.getMessage());
        }
    }
}
