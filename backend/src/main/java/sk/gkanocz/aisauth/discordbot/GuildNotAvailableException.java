package sk.gkanocz.aisauth.discordbot;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class GuildNotAvailableException extends DomainException {

    private GuildNotAvailableException(String message) {
        super(message);
    }

    public static GuildNotAvailableException botNotConnected() {
        return new GuildNotAvailableException("Discord bot is not connected.");
    }

    public static GuildNotAvailableException guildNotFound(String guildId) {
        return new GuildNotAvailableException("Bot is not a member of guild " + guildId + ".");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
