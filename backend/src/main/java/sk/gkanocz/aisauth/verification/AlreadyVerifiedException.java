package sk.gkanocz.aisauth.verification;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class AlreadyVerifiedException extends DomainException {

    private AlreadyVerifiedException(String message) {
        super(message);
    }

    public static AlreadyVerifiedException discordUserAlreadyVerified(String discordId) {
        return new AlreadyVerifiedException(
                "Discord user " + discordId + " is already verified in this server.");
    }

    public static AlreadyVerifiedException aisIdAlreadyVerified(String aisId) {
        return new AlreadyVerifiedException(
                "AIS ID " + aisId + " is already verified by another Discord user in this server.");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.CONFLICT;
    }
}
