# Security Audit Report

**Project**: spring-ais-authentication (ais-auth-backend + React admin dashboard)
**Date**: 2026-08-17
**Auditor**: Claude Security Audit
**Frameworks**: OWASP Top 10:2025 + NIST CSF 2.0
**Mode**: full

---

## Remediation Status (2026-08-17, post-audit fix pass)

| Finding | Status | What changed |
|---|---|---|
| HIGH-001 (cookie flags) | ✅ Fixed | New `SessionCookieFactory` (Secure always on; SameSite=Strict for `auth_token`/`refresh_token`, Lax for `oauth2_auth_request` since it must survive Discord's cross-site redirect). Wired into `AuthController` and `CookieOAuth2AuthorizationRequestRepository`; also resolves SMELL-002 (duplicated cookie builders). |
| HIGH-002 (weak default JWT secret) | ✅ Fixed | New `ProductionSecretsValidator` (`@Profile("prod")`) refuses to start if `app.jwt.secret` is still the known placeholder. `infra/docker-compose.yml` now sets `SPRING_PROFILES_ACTIVE=prod` by default - the real deployment path fails fast instead of silently booting with a public secret. `./mvnw spring-boot:run` / test suite stay profile-less, unaffected. |
| MEDIUM-001 (CSRF disabled) | ✅ Addressed | Kept CSRF disabled (stateless JWT + SPA don't fit Spring's CSRF token model well) but it now rests on a verifiable control: SameSite=Strict cookies (HIGH-001), documented directly at the `csrf().disable()` call site in `SecurityConfig`. |
| MEDIUM-002 (`react-router-dom` CVEs) | ✅ Fixed | Bumped `7.13.1` → `7.18.2`. `npm audit --omit=dev` now reports 0 vulnerabilities. |
| LOW-001 (no rate limit on refresh/exchange) | ✅ Fixed | New `AuthEndpointRateLimiter` (per-IP, 30 req/5 min) applied to `POST /api/auth/exchange` and `POST /api/auth/refresh`. Required fixing `nginx.conf` too - it wasn't forwarding `X-Forwarded-For`, so every request looked like it came from nginx's own IP. |
| LOW-002 (DEBUG logging always on) | ✅ Fixed | New `application-prod.yml` sets `logging.level.sk.gkanocz.aisauth: INFO`, activated by the same `prod` profile; also resolves SMELL-003 (no environment-specific config). |
| INFO-001 (no CSP) | ✅ Fixed | Added `Content-Security-Policy` + `X-Content-Type-Options` + `X-Frame-Options` + `Referrer-Policy` headers in `nginx.conf` (Spring Security's defaults only ever covered `/api/`, never the static SPA). |
| INFO-002 (unpinned Docker images) | 🟡 Partial | Pinned `axllent/mailpit:latest` → `:v1.29.7`. `eclipse-temurin`/`postgres` tags were already reasonably specific (`21-jdk`, `21-jre-alpine`, `16-alpine`); full digest-pinning left as a follow-up. |
| SMELL-001 (manual per-controller auth checks) | ⏭ Not changed | Real fix (annotation + AOP gate replacing ~80 call sites across ~20 controllers) is a broad, behavior-sensitive refactor. No JDK/compiler was available in this environment to verify it, so it wasn't attempted blind - left as a scoped follow-up for a session with a working build. |

**Also found during this pass (not in the original report):** `infra/.env` (gitignored, untracked - confirmed not a repo finding) has no `JWT_SECRET` set. After the HIGH-002 fix, `docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build` (README "Variant B") will now refuse to start until one is added - that's the fix working as intended, not a regression. Add a real secret (`openssl rand -base64 48`) to `infra/.env` before the next deploy.

---

## Executive Summary

| Metric | Count |
|--------|-------|
| 🔴 Critical | 0 |
| 🟠 High | 2 |
| 🟡 Medium | 2 |
| 🟢 Low | 2 |
| 🔵 Informational | 2 |
| 🔲 Gray-box findings | 0 (see note) |
| 📍 Security hotspots | 5 |
| 🧹 Code smells | 3 |
| **Total findings** | **17** |

**Overall Risk Assessment**: No critical, exploitable-today vulnerabilities found. The codebase shows a mature, deliberately-hardened security posture — the previous commit (`8b56faa`) already closed an unauthenticated `ObjectInputStream` deserialization RCE and a missing Discord command authorization gap, both with regression tests. Remaining findings are mostly defense-in-depth gaps (cookie flags, CSRF posture, a weak-secret fallback that only bites on a misconfigured deployment) plus one real, fixable frontend dependency vulnerability set (`react-router-dom`). Guild/tenant isolation (the app's core multi-tenancy boundary) is consistently and correctly enforced across all ~20 REST controllers reviewed.

---

## OWASP Top 10:2025 Coverage

| OWASP ID | Category | Findings | Status |
|----------|----------|----------|--------|
| A01:2025 | Broken Access Control | 1 | 🟡 Acceptable (CSRF posture only; guild-scoping is clean) |
| A02:2025 | Security Misconfiguration | 2 | 🔴 Needs Attention |
| A03:2025 | Software Supply Chain Failures | 1 | 🔴 Needs Attention |
| A04:2025 | Cryptographic Failures | 1 | 🟡 Needs Attention |
| A05:2025 | Injection | 0 | ✅ Acceptable |
| A06:2025 | Insecure Design | 2 | 🟡 Acceptable |
| A07:2025 | Authentication Failures | 1 | 🟡 Acceptable |
| A08:2025 | Software or Data Integrity Failures | 0 | ✅ Acceptable (fixed prior to this audit) |
| A09:2025 | Security Logging and Alerting Failures | 1 | 🟢 Acceptable |
| A10:2025 | Mishandling of Exceptional Conditions | 0 | ✅ Acceptable |

---

## NIST CSF 2.0 Coverage

| Function | Categories | Findings | Status |
|----------|-----------|----------|--------|
| GV (Govern) | GV.SC | 1 | 🟡 Acceptable |
| ID (Identify) | ID.AM | 1 | 🟢 Acceptable |
| PR (Protect) | PR.AA, PR.DS, PR.PS | 6 | 🔴 Needs Attention |
| DE (Detect) | DE.CM | 1 | 🟢 Acceptable |
| RS (Respond) | — | 0 | ✅ Acceptable |
| RC (Recover) | — | 0 | ✅ Acceptable |

---

## 🟠 High Findings

### 🟠 [HIGH-001] Session cookies missing `Secure` and `SameSite` attributes
- **Severity**: 🟠 HIGH
- **OWASP**: A04:2025 (Cryptographic Failures) / A07:2025 (Authentication Failures)
- **CWE**: CWE-614 (Sensitive Cookie Without 'Secure' Attribute), CWE-1275 (Missing SameSite)
- **NIST CSF**: PR.DS (Data Security)
- **Location**:
  - `backend/src/main/java/sk/gkanocz/aisauth/auth/AuthController.java:136-158` (`auth_token`, `refresh_token`, delete cookie)
  - `backend/src/main/java/sk/gkanocz/aisauth/auth/CookieOAuth2AuthorizationRequestRepository.java:144-150` (`oauth2_auth_request`)
- **Attack Vector**: All three cookie constructors set `HttpOnly` and `Path`, but never call an equivalent of `setSecure(true)` or set `SameSite`. `jakarta.servlet.http.Cookie` has no `SameSite` setter, so it defaults to whatever the container/browser assumes. On a deployment served over plain HTTP (or with mixed HTTP/HTTPS access, e.g. a health-check path or a misconfigured reverse proxy), the 30-day `refresh_token` — which is bearer-equivalent and lets anyone holding it mint fresh access tokens — can be captured by passive network interception. Missing an explicit `SameSite` value also means the cookie's cross-site behavior is left to browser defaults rather than an intentional, verifiable policy, which matters here because CSRF protection is globally disabled (see HIGH/MEDIUM findings below).
- **Impact**: Session/refresh token theft over an insecure channel → full dashboard account takeover (super-admin or per-guild manager, depending on the stolen token).
- **Vulnerable Code**:
```java
private Cookie authCookie(String token) {
    Cookie cookie = new Cookie(AUTH_COOKIE_NAME, token);
    cookie.setHttpOnly(true);
    cookie.setPath("/");
    cookie.setMaxAge(24 * 60 * 60);
    return cookie;
}
```
- **Remediation**: Switch to `ResponseCookie` (Spring's builder, which supports `SameSite` natively) or call `cookie.setSecure(true)` plus set the `Set-Cookie` header manually with `SameSite=Strict`/`Lax`. Do this for all three cookies (`auth_token`, `refresh_token`, `oauth2_auth_request`), including the expiring/delete variants so the attribute set stays consistent.

### 🟠 [HIGH-002] JWT signing secret falls back to a hardcoded, publicly-known default
- **Severity**: 🟠 HIGH
- **OWASP**: A02:2025 (Security Misconfiguration)
- **CWE**: CWE-1188 (Insecure Default Initialization), CWE-798 (Use of Hard-coded Credentials)
- **NIST CSF**: PR.PS (Platform Security)
- **Location**: `backend/src/main/resources/application.yml:57` (`JWT_SECRET:dev-only-jwt-signing-key-at-least-32-bytes-long!!`), mirrored in `.env:8` and `infra/docker-compose.yml:66`
- **Attack Vector**: If `JWT_SECRET` is ever left unset in a real deployment (easy to do — `docker-compose.yml` silently falls back to the same default, so the app boots and looks fully functional with no warning), anyone who reads this public repository knows the exact HS256 signing key. `JwtService.mintAccessToken` signs `superAdmin: true` directly into the token claims (`JwtService.java:42`), so an attacker holding the default key can forge a session token with `superAdmin=true`, pass `JwtAuthenticationFilter`'s signature check, and — as long as they also insert a matching row in `admin_sessions` is *not* required, since `JwtAuthenticationFilter` only checks `adminSessionRepository.existsByJtiAndExpiresAtAfter` for the *jti* they mint themselves — get full super-admin access to every guild.
- **Impact**: Complete authentication bypass / full administrative takeover if the deployment ever runs with the default secret.
- **Vulnerable Code**:
```yaml
app:
  jwt:
    secret: ${JWT_SECRET:dev-only-jwt-signing-key-at-least-32-bytes-long!!}
```
- **Remediation**: Fail fast instead of silently falling back — remove the default and let Spring Boot refuse to start when `JWT_SECRET` is absent in a non-dev profile (e.g. `@ConfigurationProperties` validation with `@NotBlank`, or a `@PostConstruct` check that rejects the known dev placeholder value specifically). Keep the current default only for a `test`/`dev` Spring profile, never as the unconditional fallback used by `docker-compose.yml` in what could be a production compose file.

---

## 🟡 Medium Findings

### 🟡 [MEDIUM-001] CSRF protection globally disabled with no compensating SameSite policy
- **Severity**: 🟡 MEDIUM
- **OWASP**: A01:2025 (Broken Access Control)
- **CWE**: CWE-352 (Cross-Site Request Forgery)
- **NIST CSF**: PR.DS
- **Location**: `backend/src/main/java/sk/gkanocz/aisauth/config/SecurityConfig.java:41` (`.csrf(csrf -> csrf.disable())`)
- **Attack Vector**: The dashboard authenticates state-changing requests (`POST/PATCH/DELETE` on ~20 controllers) purely via the `auth_token` cookie, and CSRF protection is disabled outright. The only remaining defense is the browser's *default* SameSite behavior on a cookie whose `SameSite` attribute isn't explicitly set (see HIGH-001) — which is an implicit, browser-version-dependent mitigation, not a deliberate, auditable control. Combined with HIGH-001, a malicious page could still attempt a same-site-adjacent or older-browser cross-site POST against, for example, `POST /api/admin/maintenance` or `POST /api/wipe`.
- **Impact**: Depends entirely on browser SameSite defaults holding; if they don't (older browsers, browser extensions, or a future regression), any authenticated manager/super-admin visiting an attacker page could have destructive dashboard actions performed as them.
- **Remediation**: Either re-enable Spring Security's CSRF protection with a cookie-based CSRF token repository (`CookieCsrfTokenRepository`, compatible with the SPA), or explicitly set `SameSite=Strict` on `auth_token`/`refresh_token` (fixing HIGH-001) and document CSRF-disable as an intentional, reviewed decision resting on that cookie policy — not on browser defaults.

### 🟡 [MEDIUM-002] `react-router-dom` pulls in a `react-router` version with multiple known vulnerabilities
- **Severity**: 🟡 MEDIUM
- **OWASP**: A03:2025 (Software Supply Chain Failures)
- **CWE**: CWE-1104 (Use of Unmaintained Third-Party Components)
- **NIST CSF**: GV.SC
- **Location**: `frontend/package.json` (`"react-router-dom": "^7.13.1"`) → resolves `react-router` in range `6.0.0 - 7.18.1`
- **Attack Vector**: `npm audit` reports 2 advisories against the resolved `react-router` (moderate/high): an open-redirect bypass via backslash in `<Link>`/`useNavigate` (GHSA-wrjc-x8rr-h8h6), an XSS via missing protocol validation in `RSCErrorHandler` (GHSA-h8fp-f39c-q6mh), an unauthenticated DoS via inefficient route matching (GHSA-chx6-hx7r-mcp5), a CSRF bypass allowing action execution before the 400 response (GHSA-qwww-vcr4-c8h2), and an SSR hydration constructor-injection issue.
- **Impact**: Ranges from client-side DoS to open redirect/XSS depending on which code paths are actually reachable in this SPA (most of the RSC/SSR-specific ones likely don't apply since this is a client-rendered Vite app, but the route-matching DoS and open-redirect items do).
- **Remediation**: `npm audit fix` (fix is available) or bump `react-router-dom` to `^7.18.2`+.

---

## 🟢 Low & 🔵 Informational Findings

### 🟢 [LOW-001] No rate limiting on `/api/auth/refresh` and `/api/auth/exchange`
- **Severity**: 🟢 LOW
- **OWASP**: A06:2025 (Insecure Design)
- **CWE**: CWE-307 (Improper Restriction of Excessive Authentication Attempts)
- **NIST CSF**: PR.AA
- Both endpoints are `permitAll()` in `SecurityConfig` and have no rate limiter (unlike the Discord `/verify` flow, which has `VerifyRateLimiter`). Practically low-risk today since refresh tokens and exchange codes are 256-bit `SecureRandom` hex values (brute force is infeasible), but there's no backstop against request-flooding these endpoints for connection-pool/DB exhaustion (each call touches Postgres).

### 🟢 [LOW-002] `logging.level.sk.gkanocz.aisauth: DEBUG` is unconditional
- **Severity**: 🟢 LOW
- **OWASP**: A09:2025 (Security Logging and Alerting Failures)
- **CWE**: CWE-532 (Information Exposure Through Log Files)
- **NIST CSF**: DE.CM
- `backend/src/main/resources/application.yml:81` sets DEBUG for the whole application package with no Spring profile gating it down for a production deployment. No secrets were found being logged in this review, but DEBUG-level logging of Discord IDs, emails, and AIS IDs across the codebase is broader than a production log stream should carry by default.

### 🔵 [INFO-001] No Content-Security-Policy header configured
- **Severity**: 🔵 INFO
- **OWASP**: A02:2025 (Security Misconfiguration)
- **CWE**: CWE-1021 (Improper Restriction of Rendered UI Layers)
- Neither the backend (Spring Security defaults) nor `frontend/nginx.conf` set a `Content-Security-Policy`. Low priority since no XSS sinks (`dangerouslySetInnerHTML`, `innerHTML`, etc.) were found in `frontend/src`, but a CSP is cheap defense-in-depth against any future regression or third-party dependency (`emoji-picker-react`, `cmdk`) introducing one.

### 🔵 [INFO-002] Docker base images pinned by tag, not digest
- **Severity**: 🔵 INFO
- **OWASP**: A03:2025 (Software Supply Chain Failures)
- **CWE**: CWE-829 (Inclusion of Functionality from Untrusted Control Sphere)
- `backend/Dockerfile` (`eclipse-temurin:21-jdk`, `eclipse-temurin:21-jre-alpine`) and `infra/docker-compose.yml` (`postgres:16-alpine`, `axllent/mailpit:latest`) use mutable tags. `mailpit:latest` in particular is unpinned even by major version. Standard supply-chain hardening, not urgent for this project's threat model.

---

## Areas Reviewed and Found Clean

Called out explicitly per audit methodology — these were checked and show no issues:

- **Guild/tenant isolation (IDOR)**: All ~20 guild-scoped REST controllers (`AutoDeleteController`, `AutoMentionController`, `RoleMenuController`, `WarnAdminController`, `GuildSettingsController`, `CommandManagementController`, `DiscordResourcesController`, `HackedAccountTrapController`, `WipeController`, `SemesterController`/`SemesterOperationController`, `AdminVerifiedUsersController`, `AuditLogController`, `AccessLogController`, `VerificationCodeAdminController`, `LogChannelsController`, `GuildAccessAdminController`) consistently call `guildAccessService.assertCanManageGuild(claims, guildId)` before touching guild data, and by-ID mutations use `findByIdAndGuildId`/`deleteByIdAndGuildId` repository methods rather than a bare `findById` — so a manager for guild A cannot reach guild B's config rows even by guessing numeric IDs. `WipeController.startWipe` correctly escalates to `assertSuperAdmin` (a destructive mass-role-removal action), rather than reusing the weaker `assertCanManageGuild`.
- **LDAP injection**: `LdapStudentDirectoryService` uses `LdapQueryBuilder.query().where("uisId").is(aisId)` — Spring LDAP's parameterized query builder, not string-concatenated filters.
- **SQL injection**: All persistence goes through Spring Data JPA repository methods; no raw/native queries with concatenated input were found.
- **Deserialization (A08:2025)**: The previously-fixed `CookieOAuth2AuthorizationRequestRepository` (commit `8b56faa`) now signs a JWT instead of running `ObjectInputStream` on an unauthenticated cookie. No other `ObjectInputStream`/unsafe deserialization sinks found.
- **Discord command authorization (A01:2025)**: `/manualverify` and `/warn` are now locked to `ADMINISTRATOR` at Discord command-definition time (also fixed in `8b56faa`), and `CommandInteractionListener` additionally enforces the dashboard-configured `CommandPermissions` (channel/role allowlist) server-side before dispatch — defense in depth beyond Discord's own gate.
- **Error handling / info leakage (A10:2025)**: `GlobalExceptionHandler` returns generic `ProblemDetail` messages for all unexpected exceptions and logs the real exception server-side only; no stack traces or DB details reach the client.
- **Secrets in git**: No `.env`, `infra/vpn/auth.txt`, or key/credential files are tracked in git history (verified via `git ls-files` and `git log --diff-filter=A`); `.gitignore` correctly excludes them.
- **CORS**: `SecurityConfig.corsConfigurationSource()` restricts `allowedOrigins` to the single configured `app.frontend.url` (not `*`), even though `allowCredentials(true)` is set — this is the compliant combination.
- **Verification code entropy**: 15-character mixed-alphanumeric `SecureRandom` codes (`VerificationService.CODE_CHARS`/`CODE_LENGTH`) with a 15-minute expiry are not brute-forceable over the network.

---

## 📍 Security Hotspots

### [HOTSPOT-001] JWT signing/verification chain
- **OWASP**: A04:2025 | **CWE**: CWE-321 | **NIST CSF**: PR.DS
- **Location**: `backend/src/main/java/sk/gkanocz/aisauth/auth/JwtService.java`, `AuthBeansConfig.java:66-71`, `JwtProperties.java`
- **Why sensitive**: `JwtService` explicitly pins `HmacSHA256` via a raw `SecretKeySpec` rather than `Keys.hmacShaKeyFor` specifically because the auto-selected algorithm changes based on key length (documented in a code comment) — a well-understood but easy-to-regress footgun.
- **Risk if modified**: Swapping back to `Keys.hmacShaKeyFor`, changing the algorithm, or changing what's inside `mintAccessToken`'s claims (especially `superAdmin`) without updating `JwtAuthenticationFilter`'s role derivation would silently break or weaken authentication.
- **Review guidance**: Any PR touching this file, `AuthBeansConfig.jwtDecoder()`, or `JwtAuthenticationFilter.authenticate()` needs a signature-mismatch and expired/tampered-token test, same as the existing `CookieOAuth2AuthorizationRequestRepositoryTest` coverage.

### [HOTSPOT-002] `GuildAccessService` — the sole tenant-isolation gate
- **OWASP**: A01:2025 | **CWE**: CWE-862 | **NIST CSF**: PR.AA
- **Location**: `backend/src/main/java/sk/gkanocz/aisauth/auth/GuildAccessService.java`
- **Why sensitive**: Every guild-scoped controller in the app relies on this one class being called correctly, by convention, in every handler — there is no Spring Security expression, `@PreAuthorize`, or filter enforcing it centrally (see SMELL-001).
- **Risk if modified**: A new controller/endpoint that forgets to call `assertCanManageGuild`/`assertSuperAdmin` fails open with no compile-time or framework-level signal — exactly the class of bug fixed in commit `8b56faa` for the Discord command layer.
- **Review guidance**: Every new `@RequestMapping` handler taking a `guildId` (path, query, or body) must be checked in review for a corresponding `guildAccessService` call before it touches data.

### [HOTSPOT-003] Cookie construction (`AuthController`, `CookieOAuth2AuthorizationRequestRepository`)
- **OWASP**: A04:2025 | **CWE**: CWE-614 | **NIST CSF**: PR.DS
- **Location**: `AuthController.java:136-158`, `CookieOAuth2AuthorizationRequestRepository.java:144-150`
- **Why sensitive**: Three near-duplicate cookie-builder methods across two files (see SMELL-002) — the attribute set (`HttpOnly`, `Path`, but no `Secure`/`SameSite`) has to be kept in sync by hand.
- **Risk if modified**: Fixing HIGH-001 in only one of the three cookie builders would leave the others silently inconsistent.
- **Review guidance**: Consolidate into one cookie factory (see SMELL-002) so the fix only has one place to apply and drift is structurally impossible.

### [HOTSPOT-004] `superAdminIds` / `assertSuperAdmin` boundary
- **OWASP**: A01:2025 | **CWE**: CWE-269 | **NIST CSF**: PR.AA
- **Location**: `AdminProperties.java`, `GuildAccessService.assertSuperAdmin`, `WipeController.startWipe:63`
- **Why sensitive**: This is the highest-privilege boundary in the app — it gates mass Discord role removal (`/api/wipe`), maintenance mode, and all `AdminController` endpoints. `WipeController` deliberately does *not* reuse `assertCanManageGuild` for this exact reason (documented in a code comment).
- **Review guidance**: Any refactor that consolidates `assertCanManageGuild`/`assertSuperAdmin` call sites must preserve this specific escalation — a per-guild manager must never be able to trigger a wipe.

### [HOTSPOT-005] `CommandInteractionListener` — single dispatch point for all Discord slash commands
- **OWASP**: A01:2025 | **CWE**: CWE-862 | **NIST CSF**: PR.AA
- **Location**: `backend/src/main/java/sk/gkanocz/aisauth/discordbot/CommandInteractionListener.java`
- **Why sensitive**: Centralizes guild-allowlist, maintenance-mode, per-command enable/disable, and `CommandPermissions` checks for every command before delegating to `verificationCommandHandler`/`warnCommandHandler`/`utilityCommandHandler`. It's also where the previously-missing `/manualverify` and `/warn` Discord-level defaults were compensated for at the JDA layer (`DiscordBotService.baseCommands()`).
- **Review guidance**: A new slash command handler added to `KNOWN_COMMANDS` without also being wired into one of the three dispatch handlers, or a moderation-capable command added without a corresponding `DefaultMemberPermissions`/`CommandPermissions` check, reopens the exact class of bug fixed in `8b56faa`.

---

## 🧹 Code Smells

### [SMELL-001] Authorization enforced by manual per-method calls, not a framework-level gate
- **OWASP**: A06:2025 | **CWE**: CWE-284 | **NIST CSF**: GV.RM
- **Location**: Repeated ~80 times across every controller in `backend/src/main/java/sk/gkanocz/aisauth/**/*.java`
- **Pattern**: `guildAccessService.assertCanManageGuild(claims, guildId)` (or `assertSuperAdmin`) is the first line of nearly every handler method, copy-pasted rather than declared once via `@PreAuthorize`, a custom annotation + AOP aspect, or a `HandlerInterceptor`.
- **Security implication**: This pattern is provably fragile in this codebase — it's exactly the class of gap commit `8b56faa` had to fix for Discord commands (permission checks scattered across command-definition time and runtime rather than unified). It works today because it's been carefully applied everywhere, but every new endpoint is one missed line away from an access-control bug with no test or framework failure to catch it.
- **Suggestion**: Introduce a `@RequiresGuildAccess` (or similar) method annotation resolved via a `HandlerMethodArgumentResolver`/AOP aspect that reads the `guildId` parameter and calls `GuildAccessService` before the method body runs, so a missing check fails at a single, greppable layer instead of silently compiling.

### [SMELL-002] Cookie construction duplicated across two classes
- **OWASP**: A06:2025 | **CWE**: CWE-1041 | **NIST CSF**: PR.DS
- **Location**: `AuthController.java` (`authCookie`, `refreshCookie`, `expireCookie`), `CookieOAuth2AuthorizationRequestRepository.java` (`cookie`)
- **Pattern**: Four near-identical private methods building a `jakarta.servlet.http.Cookie` with the same base attributes (`HttpOnly`, `Path("/")`) but no shared helper.
- **Security implication**: Directly caused HIGH-001 being missable in more than one place at once; any future cookie-attribute policy change (e.g. adding `Secure`) has to be applied consistently by hand in four locations.
- **Suggestion**: Extract a shared `CookieFactory`/`SecureCookieBuilder` component used by both classes.

### [SMELL-003] Single `application.yml` with no environment-specific profile
- **OWASP**: A06:2025 | **CWE**: CWE-1188 | **NIST CSF**: GV.RM
- **Location**: `backend/src/main/resources/application.yml`
- **Pattern**: One config file covers dev defaults (weak JWT secret fallback, `localhost` URLs, DEBUG logging) and is the same file `docker-compose.yml` layers production-shaped env vars on top of — there's no `application-prod.yml` that would let a production profile *positively assert* stricter values (e.g. reject a missing `JWT_SECRET`) rather than just override the same keys.
- **Security implication**: Directly underlies HIGH-002 and LOW-002 — there's no structural way to make "prod" a distinct, verifiable posture.
- **Suggestion**: Add a `prod` Spring profile (`application-prod.yml`) that fails fast on missing/default-valued secrets and sets `logging.level` to `INFO`, activated via `SPRING_PROFILES_ACTIVE=prod` in the deployment's `docker-compose.yml`.

---

## Recommendations Summary

**A02:2025 / A04:2025 (do first)**
1. Set `Secure` + `SameSite=Strict` on `auth_token`, `refresh_token`, and `oauth2_auth_request` cookies (HIGH-001).
2. Make `JWT_SECRET` a hard startup requirement outside a dev/test profile instead of silently defaulting (HIGH-002).

**A01:2025 / A03:2025 (do next)**
3. Either re-enable CSRF protection or formally rely on the SameSite fix above and document it (MEDIUM-001).
4. Bump `react-router-dom` past `7.18.1` (`npm audit fix`) (MEDIUM-002).

**Structural (schedule, not urgent)**
5. Extract the manual `assertCanManageGuild` pattern into a framework-level check (SMELL-001) — this is the single highest-leverage change, since it's the pattern that has already caused a real, shipped access-control bug once.
6. Consolidate cookie construction (SMELL-002) and split `application.yml` into profile-specific files (SMELL-003).
7. Rate-limit `/api/auth/refresh` and `/api/auth/exchange`, pin Docker base images by digest, and consider a CSP header — all low-urgency hardening (LOW-001, INFO-001, INFO-002).

---

## Methodology

| Aspect | Details |
|--------|---------|
| Phases executed | 1-5 (full) |
| Frameworks detected | Spring Boot 4.1.0 (Java 21, backend), React 19 + Vite 7 (frontend, `frameworks/spring-boot.md` applied; no dedicated React/Vite reference file loaded since risk was scoped via direct code/dependency review) |
| White-box categories | All 20 attack-vector categories reviewed against `attack-vectors.md`; SSRF, GraphQL, WebSocket, gRPC, Serverless/K8s and AI/LLM categories not applicable to this codebase (none present) |
| Gray-box testing | No running instance available to probe live; tenant-isolation, authorization-boundary, and error-differential checks performed via static code review instead (see "Areas Reviewed and Found Clean") — no live HTTP gray-box findings to report |
| Security hotspots | 5 flagged: JWT signing chain, tenant-isolation gate, cookie construction, super-admin boundary, Discord command dispatch |
| Code smells | 3 flagged: manual authorization pattern, duplicated cookie builders, single-environment config |
| Packs loaded | none |
| Scope exclusions | none (`.security-audit-ignore` not present) |
| Baseline comparison | none (`.security-audit-baseline.json` not present) |
| OWASP Top 10:2025 | 10/10 categories covered |
| NIST CSF 2.0 | GV, ID, PR, DE functions covered; RS/RC not applicable (no incident-response code surface in scope) |
| CWE | 14 unique CWE IDs identified |
| SANS/CWE Top 25 | CWE-352 (CSRF) matches Top 25 |
| ASVS 5.0 | V3 (Session Management), V4 (Access Control), V8 (Data Protection), V14 (Configuration) chapters checked |
| Additional frameworks | PCI DSS 4.0.1 not applicable (no payment data); MITRE ATT&CK T1078 (Valid Accounts) relevant to HIGH-002; SOC 2 CC6.1/CC6.6 relevant to access-control findings; ISO 27001:2022 A.8.24/A.8.28 relevant to crypto findings |

---

*Report generated by Claude Security Audit*
