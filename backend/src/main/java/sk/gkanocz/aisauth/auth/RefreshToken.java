package sk.gkanocz.aisauth.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The refresh token's row IS the session baseline (username/avatar/superAdmin/guildIds) - what
 * Keycloak's per-user attributes used to hold. Needed so /api/auth/refresh can still mint a valid
 * access token when the bot is momentarily disconnected and live eligibility can't be recomputed
 * (see AuthController.refresh), without decoding an already-expired access token to recover them.
 */
@Getter
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @Column(length = 64)
    private String token;

    @Column(name = "user_id", nullable = false, length = 32)
    private String userId;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "avatar", length = 64)
    private String avatar;

    @Column(name = "super_admin", nullable = false)
    private boolean superAdmin;

    @Column(name = "guild_ids", nullable = false, length = 2000)
    private String guildIdsCsv;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Set instead of deleting the row on first {@link RefreshTokenService#consume}, so a second
     * presentation of the same (now-consumed) token can be told apart from an unknown/expired one -
     * that's what lets {@link RefreshTokenService} detect token replay and revoke the rest of the
     * session family. The row still ages out naturally via {@code expires_at} (see
     * ExpiredDataCleanupJob), so no separate retention window is needed.
     */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected RefreshToken() {
        // JPA
    }

    public RefreshToken(
            String token, String userId, String username, String avatar,
            boolean superAdmin, List<String> guildIds, LocalDateTime expiresAt) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.avatar = avatar;
        this.superAdmin = superAdmin;
        this.guildIdsCsv = String.join(",", guildIds);
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public List<String> guildIds() {
        return guildIdsCsv == null || guildIdsCsv.isBlank() ? List.of() : List.of(guildIdsCsv.split(","));
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    void markRevoked() {
        this.revokedAt = LocalDateTime.now();
    }
}
