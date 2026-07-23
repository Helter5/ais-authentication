package sk.gkanocz.aisauth.ticket;

import org.springframework.http.HttpStatus;
import sk.gkanocz.aisauth.shared.DomainException;

public class TicketNotFoundException extends DomainException {

    private TicketNotFoundException(String message) {
        super(message);
    }

    public static TicketNotFoundException withChannelId(String channelId) {
        return new TicketNotFoundException("Ticket not found for channel " + channelId + ".");
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
