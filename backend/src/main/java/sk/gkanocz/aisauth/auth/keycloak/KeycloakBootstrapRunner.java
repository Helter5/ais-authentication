package sk.gkanocz.aisauth.auth.keycloak;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Best-effort startup bootstrap: never blocks app startup on Keycloak being reachable (mirrors
 * DiscordBotService's tolerance of a missing/unreachable Discord connection) - if this fails,
 * Discord login via Keycloak won't work until it succeeds, but the rest of the app still boots.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakBootstrapRunner implements ApplicationRunner {

    private final KeycloakAdminClient keycloakAdminClient;

    @Override
    public void run(ApplicationArguments args) {
        try {
            keycloakAdminClient.ensureClaimsScopeConfigured();
            log.info("Keycloak claims scope verified/configured");
        } catch (Exception e) {
            log.warn("Could not configure Keycloak claims scope on startup: {}", e.getMessage());
        }
        try {
            keycloakAdminClient.ensureSessionLifetimesConfigured();
            log.info("Keycloak session lifetimes verified/configured");
        } catch (Exception e) {
            log.warn("Could not configure Keycloak session lifetimes on startup: {}", e.getMessage());
        }
    }
}
