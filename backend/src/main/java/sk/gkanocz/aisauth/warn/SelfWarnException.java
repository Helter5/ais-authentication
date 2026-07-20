package sk.gkanocz.aisauth.warn;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class SelfWarnException extends DomainException {

    private SelfWarnException(String message) {
        super(message);
    }

    public static SelfWarnException create() {
        return new SelfWarnException("You cannot warn yourself.");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
