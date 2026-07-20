package sk.gkanocz.aisauth.verification;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class InvalidVerificationCodeException extends DomainException {

    private InvalidVerificationCodeException(String message) {
        super(message);
    }

    public static InvalidVerificationCodeException missingOrExpired() {
        return new InvalidVerificationCodeException("Verification code is missing or expired.");
    }

    public static InvalidVerificationCodeException wrongCode() {
        return new InvalidVerificationCodeException("Invalid verification code.");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
