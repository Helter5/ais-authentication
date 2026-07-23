package sk.gkanocz.aisauth.semester;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class SemesterOperationInProgressException extends DomainException {

    private SemesterOperationInProgressException(String message) {
        super(message);
    }

    public static SemesterOperationInProgressException withMessage(String message) {
        return new SemesterOperationInProgressException(message);
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.CONFLICT;
    }
}
