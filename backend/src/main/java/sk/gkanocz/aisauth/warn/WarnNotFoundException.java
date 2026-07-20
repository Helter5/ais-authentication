package sk.gkanocz.aisauth.warn;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class WarnNotFoundException extends DomainException {

    private WarnNotFoundException(String message) {
        super(message);
    }

    public static WarnNotFoundException withId(Long id) {
        return new WarnNotFoundException("Warn #" + id + " not found.");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
