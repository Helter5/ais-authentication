package sk.gkanocz.aisauth.discordbot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.discord-bot")
public record DiscordBotProperties(String token, String guildId) {
}
