package sk.gkanocz.aisauth.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.auth.JwtProperties;

/**
 * Refuses to start under the "prod" Spring profile (see infra/docker-compose.yml, which now sets
 * SPRING_PROFILES_ACTIVE=prod by default) if app.jwt.secret is still the publicly-known dev/test
 * placeholder from application.yml's default fallback. That fallback exists only so
 * `./mvnw spring-boot:run` and the test suite (neither activates a "prod" profile) boot with zero
 * config - see README. A real deployment silently inheriting the same fallback (which
 * docker-compose.yml previously did unconditionally) would let anyone who has read this public
 * repository mint a forged {@code superAdmin: true} session token, since JwtService signs that
 * claim with app.jwt.secret and JwtAuthenticationFilter trusts anything that verifies against it.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionSecretsValidator {

    private static final String DEV_PLACEHOLDER_SECRET = "dev-only-jwt-signing-key-at-least-32-bytes-long!!";

    private final JwtProperties jwtProperties;

    @PostConstruct
    public void validate() {
        if (DEV_PLACEHOLDER_SECRET.equals(jwtProperties.secret())) {
            throw new IllegalStateException(
                    "app.jwt.secret (JWT_SECRET) is still the dev placeholder value while running "
                            + "with the 'prod' profile. Set JWT_SECRET to a real random secret "
                            + "(e.g. `openssl rand -base64 48`) before deploying.");
        }
    }
}
