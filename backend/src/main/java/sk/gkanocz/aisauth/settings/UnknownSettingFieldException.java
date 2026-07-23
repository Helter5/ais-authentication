package sk.gkanocz.aisauth.settings;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class UnknownSettingFieldException extends DomainException {

    private UnknownSettingFieldException(String message) {
        super(message);
    }

    public static UnknownSettingFieldException forField(String field) {
        return new UnknownSettingFieldException("Unknown field: " + field);
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
