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
        updateUserAttributes(adminToken, userId, attributes);
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

        Map<String, Object> claims = decodeClaims(response.accessToken());
        String jti = (String) claims.get("jti");
        long expEpochSeconds = ((Number) claims.get("exp")).longValue();
        return new MintedToken(response.accessToken(), jti, expEpochSeconds);
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
        Map<String, Object> body = Map.of(
                "username", keycloakUsername(discordId),
                "enabled", true,
                // Keycloak's VERIFY_PROFILE required action blocks the later token grant unless
                // these are set - identity was already proven via Discord OAuth2, so we mark it verified.
                "email", discordId + "@discord.invalid",
                "emailVerified", true,
                "firstName", username,
                "lastName", "Discord",
                "attributes", attributes);

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

    private void updateUserAttributes(String adminToken, String userId, Map<String, List<String>> attributes) {
        restClient.put()
                .uri(adminUrl() + "/users/{id}", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("attributes", attributes))
                .retrieve()
                .toBodilessEntity();
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

    private record TokenResponse(@JsonProperty("access_token") String accessToken) {
    }

    private record CredentialRepresentation(String type, String value, boolean temporary) {
    }

    public record MintedToken(String accessToken, String jti, long expiresAtEpochSeconds) {
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
