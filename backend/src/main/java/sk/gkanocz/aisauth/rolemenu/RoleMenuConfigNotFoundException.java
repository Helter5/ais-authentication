package sk.gkanocz.aisauth.rolemenu;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class RoleMenuConfigNotFoundException extends DomainException {

    private RoleMenuConfigNotFoundException(String message) {
        super(message);
    }

    public static RoleMenuConfigNotFoundException create() {
        return new RoleMenuConfigNotFoundException("Role menu not found");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
