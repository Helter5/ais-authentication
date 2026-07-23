package sk.gkanocz.aisauth.shared;

import org.springframework.http.HttpStatus;

/**
 * Generic 400 for simple request-validation failures that don't warrant their own
 * domain-specific exception type (e.g. "missing guildId", "invalid role selection").
 */
public class InvalidRequestException extends DomainException {

    private InvalidRequestException(String message) {
        super(message);
    }

    public static InvalidRequestException withMessage(String message) {
        return new InvalidRequestException(message);
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
