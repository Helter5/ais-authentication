package sk.gkanocz.aisauth.verification;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class StudentNotFoundException extends DomainException {
    private StudentNotFoundException(String message) {
        super(message);
    }

    public static StudentNotFoundException withAisId(String aisId) {
        return new StudentNotFoundException("No student found with AIS ID " + aisId);
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
