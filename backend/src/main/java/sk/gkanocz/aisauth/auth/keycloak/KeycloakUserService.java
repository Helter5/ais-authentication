package sk.gkanocz.aisauth.auth.keycloak;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

/**
 * Orchestrates provisioning a Keycloak user for a Discord identity and minting a token for it.
 * This is what replaces JwtService.issueToken() in the login flow - Discord OAuth2 still proves
 * who the user is, Keycloak now issues and later validates the resulting session token.
 */
@Service
@RequiredArgsConstructor
public class KeycloakUserService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final KeycloakAdminClient keycloakAdminClient;

    public IssuedToken provisionAndIssueToken(
            String discordId, String username, String avatar, boolean superAdmin, List<String> guildIds) {
        String userId = keycloakAdminClient.findOrCreateUser(discordId, username, avatar, superAdmin, guildIds);

        String password = randomPassword();
        keycloakAdminClient.resetPassword(userId, password);
        KeycloakAdminClient.MintedToken minted = keycloakAdminClient.mintUserToken(discordId, password);

        LocalDateTime expiresAt =
                LocalDateTime.ofInstant(Instant.ofEpochSecond(minted.expiresAtEpochSeconds()), ZoneId.systemDefault());
        return new IssuedToken(minted.accessToken(), minted.refreshToken(), minted.jti(), expiresAt);
    }

    private String randomPassword() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public record IssuedToken(String token, String refreshToken, String jti, LocalDateTime expiresAt) {
    }
}
