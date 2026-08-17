package sk.gkanocz.aisauth.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import sk.gkanocz.aisauth.auth.JwtProperties;

/**
 * Refuses to start under the "prod" Spring profile (see infra/docker-compose.yml, which now sets
 * SPRING_PROFILES_ACTIVE=prod by default) if app.jwt.secret or the database password is still the
 * publicly-known dev/test placeholder from application.yml's default fallback. Those fallbacks
 * exist only so `./mvnw spring-boot:run` and the test suite (neither activates a "prod" profile)
 * boot with zero config - see README. A real deployment silently inheriting the JWT fallback (which
 * docker-compose.yml previously did unconditionally) would let anyone who has read this public
 * repository mint a forged {@code superAdmin: true} session token, since JwtService signs that
 * claim with app.jwt.secret and JwtAuthenticationFilter trusts anything that verifies against it.
 * The DB password fallback is the same class of risk one layer down: anyone who reaches the
 * Postgres port with the well-known default credential gets full read/write access, bypassing the
 * application entirely.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionSecretsValidator {

    private static final String DEV_PLACEHOLDER_SECRET = "dev-only-jwt-signing-key-at-least-32-bytes-long!!";
    private static final String DEV_PLACEHOLDER_DB_PASSWORD = "ais_auth";

    private final JwtProperties jwtProperties;

    @Value("${spring.datasource.password}")
    private String datasourcePassword;

    @PostConstruct
    public void validate() {
        if (DEV_PLACEHOLDER_SECRET.equals(jwtProperties.secret())) {
            throw new IllegalStateException(
                    "app.jwt.secret (JWT_SECRET) is still the dev placeholder value while running "
                            + "with the 'prod' profile. Set JWT_SECRET to a real random secret "
                            + "(e.g. `openssl rand -base64 48`) before deploying.");
        }
        if (DEV_PLACEHOLDER_DB_PASSWORD.equals(datasourcePassword)) {
            throw new IllegalStateException(
                    "spring.datasource.password (DB_PASSWORD) is still the dev placeholder value "
                            + "while running with the 'prod' profile. Set DB_PASSWORD to a real "
                            + "random secret before deploying.");
        }
    }
}
