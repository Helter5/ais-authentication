package sk.gkanocz.aisauth.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final JwtProperties PROPERTIES = new JwtProperties("secret", 300, 2_592_000);

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, PROPERTIES);
    }

    @Test
    void issueSavesAnOpaqueTokenCarryingTheFullSessionBaseline() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

        String token = refreshTokenService.issue("discord-1", "someuser", "avatar-hash", true, List.of("guild-1"));

        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(token).isEqualTo(saved.getToken());
        assertThat(token).hasSize(64); // 32 random bytes as hex
        assertThat(saved.getUserId()).isEqualTo("discord-1");
        assertThat(saved.getUsername()).isEqualTo("someuser");
        assertThat(saved.getAvatar()).isEqualTo("avatar-hash");
        assertThat(saved.isSuperAdmin()).isTrue();
        assertThat(saved.guildIds()).containsExactly("guild-1");
    }

    @Test
    void issueProducesADifferentTokenEachTime() {
        String first = refreshTokenService.issue("discord-1", "u", null, false, List.of());
        String second = refreshTokenService.issue("discord-1", "u", null, false, List.of());

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void consumeReturnsTheSessionAndDeletesTheRowSoItsSingleUse() {
        RefreshToken stored = new RefreshToken(
                "tok-1", "discord-1", "someuser", "avatar-hash", true, List.of("guild-1"),
                LocalDateTime.now().plusDays(1));
        when(refreshTokenRepository.findByTokenAndExpiresAtAfter(eq("tok-1"), any())).thenReturn(Optional.of(stored));

        RefreshTokenService.RefreshSession session = refreshTokenService.consume("tok-1");

        assertThat(session.discordId()).isEqualTo("discord-1");
        assertThat(session.username()).isEqualTo("someuser");
        assertThat(session.avatar()).isEqualTo("avatar-hash");
        assertThat(session.superAdmin()).isTrue();
        assertThat(session.guildIds()).containsExactly("guild-1");
        verify(refreshTokenRepository).deleteByToken("tok-1");
    }

    @Test
    void consumeThrowsWhenTokenIsMissingOrExpired() {
        when(refreshTokenRepository.findByTokenAndExpiresAtAfter(eq("missing"), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.consume("missing"))
                .isInstanceOf(RefreshTokenInvalidException.class);
        verify(refreshTokenRepository, never()).deleteByToken(any());
    }

    @Test
    void revokeDeletesTheToken() {
        refreshTokenService.revoke("tok-1");

        verify(refreshTokenRepository).deleteByToken("tok-1");
    }
}
