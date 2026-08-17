package sk.gkanocz.aisauth.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Single place that builds every cookie this app sets (auth_token, refresh_token,
 * oauth2_auth_request) so the security-relevant attribute set can't drift between call sites - see
 * the security audit finding this fixes: HttpOnly alone was set consistently, but Secure/SameSite
 * were missing everywhere because {@code jakarta.servlet.http.Cookie} (used directly before this
 * class existed) has no SameSite support at all, and Secure was simply never added.
 *
 * <p>{@code Secure} is always on: modern browsers treat {@code localhost} as a secure context even
 * over plain HTTP, so this doesn't break the documented `./mvnw spring-boot:run` / `docker compose`
 * local dev flow (see README), and a real deployment must be behind HTTPS regardless.
 *
 * <p>{@code SameSite} differs per cookie on purpose:
 * <ul>
 *   <li>{@code auth_token}/{@code refresh_token} are only ever read from same-origin XHR/fetch calls
 *   made by the SPA (nginx proxies /api/ on the same origin as the frontend, see nginx.conf) - never
 *   needed cross-site, so {@code Strict} is safe and is also what makes disabling Spring Security's
 *   CSRF filter (see SecurityConfig) a deliberate, verifiable decision instead of one resting on
 *   browser SameSite defaults.</li>
 *   <li>{@code oauth2_auth_request} MUST be {@code Lax}: it has to survive the cross-site top-level
 *   GET redirect Discord sends the browser back to {@code /api/auth/discord/callback} with after the
 *   user approves the OAuth prompt. {@code Strict} would drop it on exactly that request and break
 *   login.</li>
 * </ul>
 */
@Component
public class SessionCookieFactory {

    public void setStrict(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        write(response, build(name, value, maxAgeSeconds, "Strict"));
    }

    public void setLax(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        write(response, build(name, value, maxAgeSeconds, "Lax"));
    }

    public void expireStrict(HttpServletResponse response, String name) {
        setStrict(response, name, "", 0);
    }

    public void expireLax(HttpServletResponse response, String name) {
        setLax(response, name, "", 0);
    }

    private ResponseCookie build(String name, String value, int maxAgeSeconds, String sameSite) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }

    private void write(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
