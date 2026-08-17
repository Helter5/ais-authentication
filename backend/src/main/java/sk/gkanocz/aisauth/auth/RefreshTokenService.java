package sk.gkanocz.aisauth.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * Opaque, DB-backed refresh tokens - the replacement for Keycloak's SSO session refresh grant.
 * Unlike the access token, this doesn't need to carry a verifiable signature: the row's mere
 * existence (and non-expiry) in {@code refresh_tokens} IS the validity check, same pattern
 * {@link OAuthExchangeStore} already uses for one-time exchange codes. Single-use: {@link #consume}
 * marks the row revoked instead of deleting it (see {@link RefreshToken#markRevoked()}), so a
 * second presentation of the same token - the signature of a stolen/replayed token, since the
 * legitimate rotation already happened once - is distinguishable from an unknown or expired one
 * and triggers {@link #revokeAllSessionsFor}. Callers issue a fresh token to rotate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final AdminSessionRepository adminSessionRepository;
    private final JwtProperties jwtProperties;

    public String issue(String userId, String username, String avatar, boolean superAdmin, List<String> guildIds) {
        String token = randomHex(32);
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenTtlSeconds());
        refreshTokenRepository.save(
                new RefreshToken(token, userId, username, avatar, superAdmin, guildIds, expiresAt));
        return token;
    }

    @Transactional
    public RefreshSession consume(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(RefreshTokenInvalidException::create);

        if (refreshToken.isRevoked()) {
            log.warn("Refresh token reuse detected for user {} - revoking all sessions", refreshToken.getUserId());
            revokeAllSessionsFor(refreshToken.getUserId());
            throw RefreshTokenInvalidException.create();
        }
        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw RefreshTokenInvalidException.create();
        }

        refreshToken.markRevoked();
        return new RefreshSession(
                refreshToken.getUserId(), refreshToken.getUsername(), refreshToken.getAvatar(),
                refreshToken.isSuperAdmin(), refreshToken.guildIds());
    }

    public void revoke(String token) {
        refreshTokenRepository.deleteByToken(token);
    }

    private void revokeAllSessionsFor(String userId) {
        refreshTokenRepository.deleteByUserId(userId);
        adminSessionRepository.deleteByUserId(userId);
    }

    private String randomHex(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public record RefreshSession(
            String discordId, String username, String avatar, boolean superAdmin, List<String> guildIds) {
    }
}
