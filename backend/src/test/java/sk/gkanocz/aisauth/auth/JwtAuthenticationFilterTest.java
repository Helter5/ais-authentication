package sk.gkanocz.aisauth.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtDecoder jwtDecoder;
    @Mock
    private AdminSessionRepository adminSessionRepository;
    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtDecoder, adminSessionRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Jwt jwt(Map<String, Object> claims) {
        return new Jwt("raw", Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "HS256"), claims);
    }

    @Test
    void requestWithNoTokenIsLeftUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void validBearerTokenWithLiveSessionAuthenticatesAsManager() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer sometoken");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtDecoder.decode("sometoken")).thenReturn(
                jwt(Map.of("sub", "discord-1", "jti", "jti-1", "superAdmin", false)));
        when(adminSessionRepository.existsByJtiAndExpiresAtAfter(eq("jti-1"), any(LocalDateTime.class))).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_MANAGER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void validTokenWithSuperAdminClaimAuthenticatesAsSuperAdmin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer sometoken");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtDecoder.decode("sometoken")).thenReturn(
                jwt(Map.of("sub", "discord-1", "jti", "jti-1", "superAdmin", true)));
        when(adminSessionRepository.existsByJtiAndExpiresAtAfter(eq("jti-1"), any(LocalDateTime.class))).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_SUPER_ADMIN");
    }

    @Test
    void tokenFromCookieIsAlsoAccepted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("auth_token", "cookietoken"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtDecoder.decode("cookietoken")).thenReturn(jwt(Map.of("sub", "discord-1", "jti", "jti-1")));
        when(adminSessionRepository.existsByJtiAndExpiresAtAfter(eq("jti-1"), any(LocalDateTime.class))).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void sessionRevokedLeavesRequestUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer sometoken");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtDecoder.decode("sometoken")).thenReturn(jwt(Map.of("sub", "discord-1", "jti", "jti-1")));
        when(adminSessionRepository.existsByJtiAndExpiresAtAfter(eq("jti-1"), any(LocalDateTime.class))).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidTokenDoesNotThrowAndLeavesRequestUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer garbage");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtDecoder.decode("garbage")).thenThrow(new JwtException("bad signature"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
