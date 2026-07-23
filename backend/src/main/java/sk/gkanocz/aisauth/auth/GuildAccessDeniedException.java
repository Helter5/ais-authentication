package sk.gkanocz.aisauth.auth;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class GuildAccessDeniedException extends DomainException {

    private GuildAccessDeniedException(String message) {
        super(message);
    }

    public static GuildAccessDeniedException superAdminRequired() {
        return new GuildAccessDeniedException("Forbidden: Super Admin access required.");
    }

    public static GuildAccessDeniedException managerAccessRequired() {
        return new GuildAccessDeniedException("Forbidden: Manager access required for this server.");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.FORBIDDEN;
    }
}
