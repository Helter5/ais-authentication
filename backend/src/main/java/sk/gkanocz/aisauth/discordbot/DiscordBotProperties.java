package sk.gkanocz.aisauth.discordbot;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.List;

@ConfigurationProperties(prefix = "app.discord-bot")
public record DiscordBotProperties(String token, String guildId) {

    /**
     * guildId supports a comma-separated list (bots running across multiple guilds, mirroring
     * admin_settings' allowed_guild_ids) - empty entries from stray commas/whitespace are dropped.
     */
    public List<String> guildIds() {
        if (!StringUtils.hasText(guildId)) {
            return List.of();
        }
        return List.of(guildId.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
