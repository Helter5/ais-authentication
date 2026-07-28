package sk.gkanocz.aisauth.automod;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class AutoMentionNotFoundException extends DomainException {

    private AutoMentionNotFoundException(String message) {
        super(message);
    }

    public static AutoMentionNotFoundException create() {
        return new AutoMentionNotFoundException("Not found");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
