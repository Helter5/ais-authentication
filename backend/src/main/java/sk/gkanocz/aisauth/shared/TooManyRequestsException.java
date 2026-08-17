package sk.gkanocz.aisauth.shared;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends DomainException {

    private TooManyRequestsException(String message) {
        super(message);
    }

    public static TooManyRequestsException withMessage(String message) {
        return new TooManyRequestsException(message);
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}
