package sk.gkanocz.aisauth.auth;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class RefreshTokenInvalidException extends DomainException {

    private RefreshTokenInvalidException(String message) {
        super(message);
    }

    public static RefreshTokenInvalidException create() {
        return new RefreshTokenInvalidException("Refresh token missing, expired, or revoked");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.UNAUTHORIZED;
    }
}
