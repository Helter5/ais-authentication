package sk.gkanocz.aisauth.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.discord")
public record DiscordOAuthProperties(
    String clientId,
    String clientSecret,
    String redirectUri){
    
}
