package sk.gkanocz.aisauth.auth;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;
import sk.gkanocz.aisauth.admin.AccessLogRepository;
import sk.gkanocz.aisauth.discordbot.DiscordBotService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    private static final String FRONTEND_URL = "http://localhost:5173";

    @Mock
    private EligibleGuildsResolver eligibleGuildsResolver;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AdminSessionRepository adminSessionRepository;
    @Mock
    private AccessLogRepository accessLogRepository;
    @Mock
    private DiscordBotService discordBotService;
    @Mock
    private Authentication authentication;

    private OAuth2LoginSuccessHandler handler;
    private OAuthExchangeStore exchangeStore;

    @BeforeEach
    void setUp() {
        exchangeStore = new OAuthExchangeStore();
        handler = new OAuth2LoginSuccessHandler(
                new AdminProperties(List.of("super-admin-1")),
                eligibleGuildsResolver, jwtService, refreshTokenService,
                adminSessionRepository, accessLogRepository, discordBotService, exchangeStore,
                new ClientIpResolver());
        ReflectionTestUtils.setField(handler, "frontendUrl", FRONTEND_URL);
    }

    private OAuth2User discordUser(String id) {
        return new DefaultOAuth2User(
                Set.of(), Map.of("id", id, "username", "someuser", "avatar", "avatar-hash"), "id");
    }

    @Test
    void redirectsWithBotNotReadyWhenBotIsDisconnected() throws Exception {
        when(discordBotService.jda()).thenReturn(Optional.empty());
        when(authentication.getPrincipal()).thenReturn(discordUser("discord-1"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_URL + "/login?error=bot_not_ready");
        verify(jwtService, never()).mintAccessToken(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void redirectsWithUnauthorizedWhenRegularUserHasNoEligibleGuilds() throws Exception {
        when(discordBotService.jda()).thenReturn(Optional.of(mock(JDA.class)));
        when(authentication.getPrincipal()).thenReturn(discordUser("discord-1"));
        when(eligibleGuildsResolver.computeEligibleGuildIds("discord-1", false)).thenReturn(List.of());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND_URL + "/login?error=unauthorized_manager_required");
    }

    @Test
    void superAdminWithNoGuildsStillLogsInSuccessfully() throws Exception {
        when(discordBotService.jda()).thenReturn(Optional.of(mock(JDA.class)));
        when(authentication.getPrincipal()).thenReturn(discordUser("super-admin-1"));
        when(eligibleGuildsResolver.computeEligibleGuildIds("super-admin-1", true)).thenReturn(List.of());
        when(jwtService.mintAccessToken(eq("super-admin-1"), eq("someuser"), eq("avatar-hash"), eq(true), eq(List.of())))
                .thenReturn(new JwtService.IssuedAccessToken("token-1", "jti-1", LocalDateTime.now().plusMinutes(5)));
        when(refreshTokenService.issue(eq("super-admin-1"), any(), any(), eq(true), any())).thenReturn("refresh-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl()).startsWith(FRONTEND_URL + "/select-server?code=");
        verify(accessLogRepository, never()).save(any());
    }

    @Test
    void successfulLoginMintsTokenSavesSessionAndRedirectsWithExchangeCode() throws Exception {
        when(discordBotService.jda()).thenReturn(Optional.of(mock(JDA.class)));
        when(authentication.getPrincipal()).thenReturn(discordUser("discord-1"));
        when(eligibleGuildsResolver.computeEligibleGuildIds("discord-1", false)).thenReturn(List.of("guild-1", "guild-2"));
        JwtService.IssuedAccessToken issued = new JwtService.IssuedAccessToken(
                "access-token-1", "jti-1", LocalDateTime.now().plusMinutes(5));
        when(jwtService.mintAccessToken("discord-1", "someuser", "avatar-hash", false, List.of("guild-1", "guild-2")))
                .thenReturn(issued);
        when(refreshTokenService.issue("discord-1", "someuser", "avatar-hash", false, List.of("guild-1", "guild-2")))
                .thenReturn("refresh-token-1");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<AdminSession> sessionCaptor = ArgumentCaptor.forClass(AdminSession.class);
        verify(adminSessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getJti()).isEqualTo("jti-1");
        assertThat(sessionCaptor.getValue().getUserId()).isEqualTo("discord-1");

        verify(accessLogRepository, times(2)).save(any());

        assertThat(response.getRedirectedUrl()).startsWith(FRONTEND_URL + "/select-server?code=");
        String code = response.getRedirectedUrl().substring((FRONTEND_URL + "/select-server?code=").length());
        OAuthExchangeStore.ExchangedTokens tokens = exchangeStore.consumeToken(code);
        assertThat(tokens.accessToken()).isEqualTo("access-token-1");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-token-1");
    }

    @Test
    void clientIpTrustsTheSecondToLastForwardedForSegmentCaddyAppendedNotNginxsOwnLastOneOrTheClientSpoofedFirstOne() throws Exception {
        when(discordBotService.jda()).thenReturn(Optional.of(mock(JDA.class)));
        when(authentication.getPrincipal()).thenReturn(discordUser("discord-1"));
        when(eligibleGuildsResolver.computeEligibleGuildIds("discord-1", false)).thenReturn(List.of("guild-1"));
        when(jwtService.mintAccessToken(any(), any(), any(), anyBoolean(), any()))
                .thenReturn(new JwtService.IssuedAccessToken("t", "jti-1", LocalDateTime.now().plusMinutes(5)));
        when(refreshTokenService.issue(any(), any(), any(), anyBoolean(), any())).thenReturn("r");

        MockHttpServletRequest request = new MockHttpServletRequest();
        // Two trusted hops in front of the backend (see infra/Caddyfile, frontend/nginx.conf):
        // Caddy is the edge and appends the real client address it saw ("198.51.100.9"); nginx
        // then appends its own upstream peer, i.e. Caddy's container IP ("172.29.0.7"). The first
        // segment, "203.0.113.5", is attacker-supplied and must never be trusted either.
        request.addHeader("X-Forwarded-For", "203.0.113.5, 198.51.100.9, 172.29.0.7");
        request.setRemoteAddr("172.17.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<sk.gkanocz.aisauth.admin.AccessLog> captor =
                ArgumentCaptor.forClass(sk.gkanocz.aisauth.admin.AccessLog.class);
        verify(accessLogRepository).save(captor.capture());
        assertThat(captor.getValue().getIp()).isEqualTo("198.51.100.9");
    }
}
