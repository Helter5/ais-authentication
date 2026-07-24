package sk.gkanocz.aisauth.shared;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import sk.gkanocz.aisauth.auth.GuildAccessDeniedException;
import sk.gkanocz.aisauth.automod.AutoDeleteConfigExistsException;
import sk.gkanocz.aisauth.ticket.TicketNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The individual status codes (400/401/403/404/409) are already exercised end-to-end through
 * real controllers elsewhere; this pins the mapping mechanism itself - every DomainException
 * subtype maps to its own declared status regardless of which controller throws it, and
 * unexpected exceptions never leak their message to the client.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void domainExceptionMapsToItsOwnDeclaredStatusAndMessage() {
        ProblemDetail problem = handler.handleDomainException(GuildAccessDeniedException.managerAccessRequired());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getTitle()).isEqualTo("Forbidden");
        assertThat(problem.getDetail()).isEqualTo("Forbidden: Manager access required for this server.");
    }

    @Test
    void differentDomainExceptionSubtypesMapToTheirOwnDistinctStatus() {
        assertThat(handler.handleDomainException(AutoDeleteConfigExistsException.forChannel()).getStatus())
                .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(handler.handleDomainException(TicketNotFoundException.withChannelId("chan-1")).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(handler.handleDomainException(InvalidRequestException.withMessage("bad input")).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void noResourceFoundMapsToNotFoundWithAGenericDetailRegardlessOfThePath() {
        NoResourceFoundException ex = new NoResourceFoundException(
                HttpMethod.GET, "/api/nonexistent/path", "No resource found");

        ProblemDetail problem = handler.handleNotFound(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("No such endpoint.");
    }

    @Test
    void unexpectedExceptionsMapTo500WithoutLeakingTheInternalMessage() {
        RuntimeException internal = new RuntimeException("db password is hunter2");

        ProblemDetail problem = handler.handleUnexpected(internal);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).doesNotContain("hunter2");
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
    }
}
