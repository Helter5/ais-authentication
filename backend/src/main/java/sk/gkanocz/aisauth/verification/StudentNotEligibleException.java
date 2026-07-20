package sk.gkanocz.aisauth.verification;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class StudentNotEligibleException extends DomainException {

    private StudentNotEligibleException(String message) {
        super(message);
    }

    public static StudentNotEligibleException notActiveStudent() {
        return new StudentNotEligibleException("Not an active student account.");
    }

    public static StudentNotEligibleException wrongFaculty() {
        return new StudentNotEligibleException("Student does not belong to an allowed faculty.");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.FORBIDDEN;
    }
}
