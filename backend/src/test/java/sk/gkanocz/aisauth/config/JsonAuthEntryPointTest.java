package sk.gkanocz.aisauth.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JsonAuthEntryPointTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonAuthEntryPoint entryPoint = new JsonAuthEntryPoint(objectMapper);

    private static class TestAuthException extends AuthenticationException {
        TestAuthException(String message) {
            super(message);
        }
    }

    @Test
    void commenceWritesA401ProblemDetail() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new TestAuthException("no token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("Missing or invalid authentication.")
                .contains("Unauthorized");
    }

    @Test
    void handleWritesA403ProblemDetail() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.handle(request, response, new AccessDeniedException("nope"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("You do not have access to this resource.")
                .contains("Forbidden");
    }
}
