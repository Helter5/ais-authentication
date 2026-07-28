package sk.gkanocz.aisauth.automod;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class AutoMentionExistsException extends DomainException {

    private AutoMentionExistsException(String message) {
        super(message);
    }

    public static AutoMentionExistsException forChannel() {
        return new AutoMentionExistsException("Auto-mention for this channel already exists");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.CONFLICT;
    }
}
