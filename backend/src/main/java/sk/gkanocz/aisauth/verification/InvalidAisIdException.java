package sk.gkanocz.aisauth.verification;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class InvalidAisIdException extends DomainException {

    private InvalidAisIdException(String message) {
        super(message);
    }

    public static InvalidAisIdException withValue(String aisId) {
        return new InvalidAisIdException("Invalid AIS ID: " + aisId);
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
