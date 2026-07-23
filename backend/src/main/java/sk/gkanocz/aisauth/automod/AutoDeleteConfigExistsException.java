package sk.gkanocz.aisauth.automod;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class AutoDeleteConfigExistsException extends DomainException {

    private AutoDeleteConfigExistsException(String message) {
        super(message);
    }

    public static AutoDeleteConfigExistsException forChannel() {
        return new AutoDeleteConfigExistsException("Config for this channel already exists");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.CONFLICT;
    }
}
