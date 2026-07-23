package sk.gkanocz.aisauth.wipe;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class WipeInProgressException extends DomainException {

    private WipeInProgressException(String message) {
        super(message);
    }

    public static WipeInProgressException create() {
        return new WipeInProgressException("Wipe already in progress for this guild");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.CONFLICT;
    }
}
