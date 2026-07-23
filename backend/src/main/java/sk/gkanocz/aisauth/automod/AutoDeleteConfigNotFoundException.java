package sk.gkanocz.aisauth.automod;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class AutoDeleteConfigNotFoundException extends DomainException {

    private AutoDeleteConfigNotFoundException(String message) {
        super(message);
    }

    public static AutoDeleteConfigNotFoundException create() {
        return new AutoDeleteConfigNotFoundException("Not found");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
