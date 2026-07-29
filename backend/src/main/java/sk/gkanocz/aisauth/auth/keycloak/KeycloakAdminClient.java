package sk.gkanocz.aisauth.auth.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to Keycloak's Admin REST API directly via RestClient (same style as DiscordOAuthClient)
 * instead of pulling in the keycloak-admin-client SDK. Provisions a Keycloak user per Discord
 * identity and mints tokens for it via a direct grant using a freshly rotated password - Keycloak
 * has no "mint a token for an already-verified identity" endpoint without a broker, and Discord
 * isn't supported as a native Keycloak identity provider.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAdminClient {

    private final KeycloakProperties keycloakProperties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public String findOrCreateUser(
            String discordId, String username, String avatar, boolean superAdmin, List<String> guildIds) {
        String adminToken = fetchAdminToken();
        Map<String, List<String>> attributes = Map.of(
                "discord_id", List.of(discordId),
                "super_admin", List.of(String.valueOf(superAdmin)),
                "guild_ids", guildIds,
                "discord_username", List.of(username),
                "avatar", List.of(avatar == null ? "" : avatar));

        String userId = findUserId(adminToken, discordId);
        if (userId == null) {
            return createUser(adminToken, discordId, username, attributes);
        }
        updateUserAttributes(adminToken, userId, discordId, username, attributes);
        return userId;
    }

    public void resetPassword(String userId, String newPassword) {
        String adminToken = fetchAdminToken();
        restClient.put()
                .uri(adminUrl() + "/users/{id}/reset-password", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CredentialRepresentation("password", newPassword, false))
                .retrieve()
                .toBodilessEntity();
    }

    public MintedToken mintUserToken(String discordId, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", keycloakProperties.tokenClientId());
        form.add("grant_type", "password");
        form.add("username", keycloakUsername(discordId));
        form.add("password", password);
        form.add("scope", "openid");

        TokenResponse response = restClient.post()
                .uri(realmUrl() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        return toMintedToken(response);
    }

    /**
     * Exchanges a still-valid refresh token for a fresh access/refresh token pair. Called from the
     * dashboard's /api/auth/refresh endpoint once the short-lived access token has expired - the
     * refresh token itself is only valid for as long as Keycloak's ssoSessionIdleTimeout/
     * ssoSessionMaxLifespan allow (see KeycloakBootstrapRunner/ensureSessionLifetimesConfigured).
     */
    public MintedToken refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", keycloakProperties.tokenClientId());
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);

        TokenResponse response;
        try {
            response = restClient.post()
                    .uri(realmUrl() + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientResponseException e) {
            throw RefreshTokenInvalidException.create();
        }
        return toMintedToken(response);
    }

    /**
     * Best-effort: ends the Keycloak SSO session backing this refresh token so it can't be used to
     * mint further access tokens after logout, on top of our own sessions-table row deletion (which
     * only revokes the already-issued access token, not the refresh token behind it).
     */
    public void revokeRefreshToken(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", keycloakProperties.tokenClientId());
        form.add("refresh_token", refreshToken);

        try {
            restClient.post()
                    .uri(realmUrl() + "/protocol/openid-connect/logout")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to revoke refresh token on logout: {}", e.getMessage());
        }
    }

    private MintedToken toMintedToken(TokenResponse response) {
        Map<String, Object> claims = decodeClaims(response.accessToken());
        String jti = (String) claims.get("jti");
        long expEpochSeconds = ((Number) claims.get("exp")).longValue();
        return new MintedToken(response.accessToken(), response.refreshToken(), jti, expEpochSeconds);
    }

    /**
     * Idempotently applies the realm's intended access/refresh-token lifetimes. Not declared in
     * realm-export.json alone because {@code --import-realm} only imports a realm that doesn't
     * already exist yet - on an already-provisioned Keycloak volume, editing the export file is a
     * silent no-op, so this reapplies the same values via the admin API on every startup.
     */
    public void ensureSessionLifetimesConfigured() {
        String adminToken = fetchAdminToken();
        Map<String, Object> realm = new HashMap<>(restClient.get()
                .uri(adminUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { }));

        Map<String, Integer> desired = Map.of(
                "accessTokenLifespan", 300,
                "ssoSessionIdleTimeout", 172800,
                "ssoSessionMaxLifespan", 2592000);

        boolean changed = false;
        for (Map.Entry<String, Integer> entry : desired.entrySet()) {
            Object current = realm.get(entry.getKey());
            if (!(current instanceof Number number) || number.intValue() != entry.getValue()) {
                realm.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        if (!changed) {
            return;
        }

        restClient.put()
                .uri(adminUrl())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(realm)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Ensures the realm allows custom ("unmanaged") user attributes to flow into tokens, and that
     * the "ais-auth-claims" client scope + its protocol mappers exist and are assigned as a default
     * scope on the token-issuing client. Idempotent, safe to call on every startup.
     *
     * Deliberately NOT declared in realm-export.json - even with everything wired up correctly
     * (mappers, scope, default-scope assignment), Keycloak 26's Declarative User Profile silently
     * drops custom user attributes from every token unless the realm's User Profile explicitly sets
     * {@code unmanagedAttributePolicy: ENABLED}; that setting isn't representable in a partial
     * realm-export the way client/mapper config is. Verified against a real Keycloak 26.0.8 container.
     */
    public void ensureClaimsScopeConfigured() {
        String adminToken = fetchAdminToken();
        enableUnmanagedUserAttributes(adminToken);

        String scopeId = findClientScopeId(adminToken, "ais-auth-claims");
        if (scopeId == null) {
            scopeId = createClaimsScope(adminToken);
        }
        ensureMappersExist(adminToken, scopeId);

        String tokenClientUuid = findClientUuid(adminToken, keycloakProperties.tokenClientId());
        assignDefaultScopeIfMissing(adminToken, tokenClientUuid, scopeId);
    }

    private void enableUnmanagedUserAttributes(String adminToken) {
        Map<String, Object> profile = restClient.get()
                .uri(adminUrl() + "/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() { });
        if ("ENABLED".equals(profile.get("unmanagedAttributePolicy"))) {
            return;
        }

        Map<String, Object> updated = new HashMap<>(profile);
        updated.put("unmanagedAttributePolicy", "ENABLED");
        restClient.put()
                .uri(adminUrl() + "/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(updated)
                .retrieve()
                .toBodilessEntity();
    }

    private String createUser(String adminToken, String discordId, String username, Map<String, List<String>> attributes) {
        Map<String, Object> body = new HashMap<>(profileFields(discordId, username));
        body.put("username", keycloakUsername(discordId));
        body.put("enabled", true);
        body.put("attributes", attributes);

        ResponseEntity<Void> response = restClient.post()
                .uri(adminUrl() + "/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private void updateUserAttributes(
            String adminToken, String userId, String discordId, String username, Map<String, List<String>> attributes) {
        Map<String, Object> body = new HashMap<>(profileFields(discordId, username));
        body.put("attributes", attributes);
        restClient.put()
                .uri(adminUrl() + "/users/{id}", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Keycloak's VERIFY_PROFILE required action silently blocks the later token grant
     * ("Account is not fully set up") unless every required profile field is populated -
     * identity was already proven via Discord OAuth2, so we mark it verified. Applied on
     * both create and update so a user who somehow exists in Keycloak without these fields
     * (e.g. created by hand) gets repaired on their next login instead of staying broken.
     */
    private Map<String, Object> profileFields(String discordId, String username) {
        return Map.of(
                "email", discordId + "@discord.invalid",
                "emailVerified", true,
                "firstName", username,
                "lastName", "Discord");
    }

    private String findUserId(String adminToken, String discordId) {
        List<Map<String, Object>> users = restClient.get()
                .uri(adminUrl() + "/users?username={username}&exact=true", keycloakUsername(discordId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
        if (users == null || users.isEmpty()) {
            return null;
        }
        return (String) users.get(0).get("id");
    }

    private String findClientScopeId(String adminToken, String name) {
        List<Map<String, Object>> scopes = restClient.get()
                .uri(adminUrl() + "/client-scopes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
        return scopes.stream()
                .filter(scope -> name.equals(scope.get("name")))
                .map(scope -> (String) scope.get("id"))
                .findFirst()
                .orElse(null);
    }

    private String createClaimsScope(String adminToken) {
        Map<String, Object> body = Map.of(
                "name", "ais-auth-claims",
                "protocol", "openid-connect",
                "attributes", Map.of(
                        "include.in.token.scope", "true",
                        "display.on.consent.screen", "false"));

        ResponseEntity<Void> response = restClient.post()
                .uri(adminUrl() + "/client-scopes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private void ensureMappersExist(String adminToken, String scopeId) {
        List<Map<String, Object>> existing = restClient.get()
                .uri(adminUrl() + "/client-scopes/{scopeId}/protocol-mappers/models", scopeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
        List<String> existingNames = existing.stream().map(m -> (String) m.get("name")).toList();

        for (ClaimMapper mapper : claimMappers()) {
            if (!existingNames.contains(mapper.name())) {
                restClient.post()
                        .uri(adminUrl() + "/client-scopes/{scopeId}/protocol-mappers/models", scopeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(mapper.toRepresentation())
                        .retrieve()
                        .toBodilessEntity();
            }
        }
    }

    private String findClientUuid(String adminToken, String clientId) {
        List<Map<String, Object>> clients = restClient.get()
                .uri(adminUrl() + "/clients?clientId={clientId}", clientId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
        return (String) clients.get(0).get("id");
    }

    private void assignDefaultScopeIfMissing(String adminToken, String clientUuid, String scopeId) {
        List<Map<String, Object>> assigned = restClient.get()
                .uri(adminUrl() + "/clients/{clientUuid}/default-client-scopes", clientUuid)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
        boolean alreadyAssigned = assigned.stream().anyMatch(scope -> scopeId.equals(scope.get("id")));
        if (alreadyAssigned) {
            return;
        }
        restClient.put()
                .uri(adminUrl() + "/clients/{clientUuid}/default-client-scopes/{scopeId}", clientUuid, scopeId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .toBodilessEntity();
    }

    private List<ClaimMapper> claimMappers() {
        return List.of(
                new ClaimMapper("discord_id", "discord_id", "discord_id", "String", false),
                new ClaimMapper("superAdmin", "super_admin", "superAdmin", "boolean", false),
                new ClaimMapper("guildIds", "guild_ids", "guildIds", "String", true),
                new ClaimMapper("username", "discord_username", "username", "String", false),
                new ClaimMapper("avatar", "avatar", "avatar", "String", false));
    }

    private String fetchAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", keycloakProperties.adminClientId());
        form.add("client_secret", keycloakProperties.adminClientSecret());
        form.add("grant_type", "client_credentials");

        TokenResponse response = restClient.post()
                .uri(realmUrl() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        return response.accessToken();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeClaims(String token) {
        String[] parts = token.split("\\.");
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        return objectMapper.readValue(new String(payload, StandardCharsets.UTF_8), Map.class);
    }

    private String keycloakUsername(String discordId) {
        return "discord-" + discordId;
    }

    private String realmUrl() {
        return keycloakProperties.serverUrl() + "/realms/" + keycloakProperties.realm();
    }

    private String adminUrl() {
        return keycloakProperties.serverUrl() + "/admin/realms/" + keycloakProperties.realm();
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken) {
    }

    private record CredentialRepresentation(String type, String value, boolean temporary) {
    }

    public record MintedToken(String accessToken, String refreshToken, String jti, long expiresAtEpochSeconds) {
    }

    private record ClaimMapper(String name, String userAttribute, String claimName, String jsonType, boolean multivalued) {
        Map<String, Object> toRepresentation() {
            return Map.of(
                    "name", name,
                    "protocol", "openid-connect",
                    "protocolMapper", "oidc-usermodel-attribute-mapper",
                    "config", Map.of(
                            "user.attribute", userAttribute,
                            "claim.name", claimName,
                            "jsonType.label", jsonType,
                            "id.token.claim", "true",
                            "access.token.claim", "true",
                            "userinfo.token.claim", "true",
                            "multivalued", String.valueOf(multivalued)));
        }
    }
}
