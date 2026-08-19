package sk.gkanocz.aisauth.thesiscounter;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class ThesisCounterConfigNotFoundException extends DomainException {

    private ThesisCounterConfigNotFoundException(String message) {
        super(message);
    }

    public static ThesisCounterConfigNotFoundException create() {
        return new ThesisCounterConfigNotFoundException("Thesis counter not found");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
