package sk.gkanocz.aisauth.auth;

import io.jsonwebtoken.Claims;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.AbstractMap;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Presents a Spring-validated {@link Jwt} as the jjwt {@link Claims} type every controller already
 * reads via {@code @AuthenticationPrincipal Claims claims} - lets the resource-server swap (jjwt
 * local verification -> Spring JwtDecoder against Keycloak's JWKS) happen without touching any
 * controller.
 *
 * <p>{@link #getSubject()} deliberately returns the {@code discord_id} claim instead of the JWT's
 * real {@code sub}: Keycloak's {@code sub} is always its own internal user UUID, but the app's
 * business logic (audit log actor id, etc.) has always used the subject as the Discord id.
 */
public class JwtClaimsAdapter extends AbstractMap<String, Object> implements Claims {

    private final Jwt jwt;

    public JwtClaimsAdapter(Jwt jwt) {
        this.jwt = jwt;
    }

    @Override
    public String getSubject() {
        Object discordId = jwt.getClaims().get("discord_id");
        return discordId != null ? discordId.toString() : jwt.getSubject();
    }

    @Override
    public String getIssuer() {
        return jwt.getIssuer() != null ? jwt.getIssuer().toString() : null;
    }

    @Override
    public Set<String> getAudience() {
        return jwt.getAudience() != null ? new LinkedHashSet<>(jwt.getAudience()) : Set.of();
    }

    @Override
    public Date getExpiration() {
        return jwt.getExpiresAt() != null ? Date.from(jwt.getExpiresAt()) : null;
    }

    @Override
    public Date getNotBefore() {
        return jwt.getNotBefore() != null ? Date.from(jwt.getNotBefore()) : null;
    }

    @Override
    public Date getIssuedAt() {
        return jwt.getIssuedAt() != null ? Date.from(jwt.getIssuedAt()) : null;
    }

    @Override
    public String getId() {
        return jwt.getId();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String claim, Class<T> type) {
        return (T) jwt.getClaims().get(claim);
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return jwt.getClaims().entrySet();
    }
}
