package sk.gkanocz.aisauth.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.auth.AdminSession;
import sk.gkanocz.aisauth.auth.AdminSessionRepository;
import sk.gkanocz.aisauth.auth.JwtService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Issues a real JWT + a matching admin_sessions row, since JwtAuthenticationFilter requires both
 * (a valid signature AND a live session row) before it'll authenticate a request. Lets
 * integration tests exercise the real security filter chain instead of mocking auth away.
 */
@Component
public class AuthenticatedRequestHelper {

    @Autowired
    private JwtService jwtService;
    @Autowired
    private AdminSessionRepository adminSessionRepository;

    public String tokenFor(String discordId, boolean superAdmin, List<String> guildIds) {
        JwtService.IssuedToken issued = jwtService.issueToken(discordId, "test_user", null, superAdmin, guildIds);
        adminSessionRepository.save(new AdminSession(issued.jti(), discordId, LocalDateTime.now().plusHours(1)));
        return issued.token();
    }

    public String superAdminToken() {
        return tokenFor("super-admin-1", true, List.of());
    }

    public String managerTokenFor(String guildId) {
        return tokenFor("manager-1", false, List.of(guildId));
    }

    public String bearer(String token) {
        return "Bearer " + token;
    }
}
