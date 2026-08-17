# Security Audit Report

**Project**: spring-ais-authentication (AIS Discord verification backend + frontend)
**Date**: 2026-08-17
**Auditor**: Claude Security Audit
**Frameworks**: OWASP Top 10:2025 + NIST CSF 2.0
**Mode**: full (re-audit — third full pass today; diff since the prior full audit was test-fixtures only, see Methodology)

---

## Remediation Status

| ID | Status | Note |
|----|--------|------|
| LOW-001 | ✅ Fixed, verified in current file | `Permissions-Policy` confirmed live at `frontend/nginx.conf:15`. HSTS left commented-out (`:19`) on purpose - no TLS in front of this stack yet. |
| LOW-002 | ➖ Skipped, deliberately | Digest-pinning `node:22-alpine` costs more (manual re-pin on every bump) than it buys (build-stage only, discarded before the pinned runtime image ships, `npm ci` already locks real deps). Not worth it. |
| LOW-003 | ✅ Fixed, verified in current file | `backend-ci.yml`: `permissions: contents: read` confirmed at `:18-19`, both actions confirmed pinned to commit SHA at `:22,25`. |
| LOW-004 | ✅ Documented, verified in current file | `infra/docker-compose.yml:95-97`: accepted-risk comment confirmed present above `LDAP_URL`. No real fix exists - STU's LDAP only speaks plain `ldap://`. |
| SMELL-001/002 | ⏳ Not applied | Refactor-only, no security impact - code blocks left in the report as a reference. |
| Test suite | ✅ Fixed | 10 CI failures from the two prior fix passes (stale super-admin test fixtures, non-snowflake guildId fixtures, a stale email-in-reply assertion) resolved in commit `aeda843` - diffed by hand this pass, confirmed fixture-only, no assertion weakened (the `/find` test actually got stricter: added `.doesNotContain("s@stuba.sk")`). |

---

## Executive Summary

| Metric | Count |
|--------|-------|
| 🔴 Critical | 0 |
| 🟠 High | 0 |
| 🟡 Medium | 0 |
| 🟢 Low | 4 |
| 🔵 Informational | 0 |
| 🔲 Gray-box findings | 0 |
| 📍 Security hotspots | 7 |
| 🧹 Code smells | 2 |
| **Total findings** | **4** |

**Overall Risk Assessment**: Low. This is the third full audit pass on this codebase in one day. The previous full pass (same day) independently re-verified every finding from the original audit against actual code and found zero regressions, plus ran a genuinely fresh sweep of all 23 backend REST controllers, all 11 Discord bot listeners, the full JWT/OAuth2/LDAP/JPA stack, and the entire frontend — zero new Critical/High/Medium findings, 4 Low-severity hardening gaps. Since that pass, `git diff` confirms the only changes were: 3 of those 4 Low findings fixed (nginx `Permissions-Policy`, CI action SHA-pinning + `permissions:` block, an accepted-risk comment for the LDAP finding that has no real fix), and 7 backend test files updated to fix CI failures caused by the two earlier fix commits — test-fixture corrections only, zero production/infra/frontend code touched. Given zero production-relevant diff since the last exhaustive sweep, this pass verified the 3 applied fixes are genuinely present in the files (not just claimed) and hand-reviewed the full test diff for any weakened assertion (none found — one assertion got strictly *more* thorough) rather than re-running a redundant full 2-agent sweep for a no-op production diff.

---

## OWASP Top 10:2025 Coverage

| OWASP ID | Category | Findings | Status |
|----------|----------|----------|--------|
| A01:2025 | Broken Access Control | 0 | ✅ Acceptable |
| A02:2025 | Security Misconfiguration | 2 | ✅ Acceptable (Low only) |
| A03:2025 | Software Supply Chain Failures | 2 | ✅ Acceptable (Low only) |
| A04:2025 | Cryptographic Failures | 1 | ✅ Acceptable (Low only) |
| A05:2025 | Injection | 0 | ✅ Acceptable |
| A06:2025 | Insecure Design | 0 | ✅ Acceptable |
| A07:2025 | Authentication Failures | 0 | ✅ Acceptable |
| A08:2025 | Software or Data Integrity Failures | 0 | ✅ Acceptable |
| A09:2025 | Security Logging and Alerting Failures | 0 | ✅ Acceptable |
| A10:2025 | Mishandling of Exceptional Conditions | 0 | ✅ Acceptable |

---

## NIST CSF 2.0 Coverage

| Function | Categories | Findings | Status |
|----------|-----------|----------|--------|
| GV (Govern) | GV.SC | 2 | ✅ Acceptable (Low only) |
| ID (Identify) | ID.RA | 0 | ✅ Acceptable |
| PR (Protect) | PR.AA, PR.DS, PR.PS | 2 | ✅ Acceptable (Low only) |
| DE (Detect) | DE.CM, DE.AE | 0 | ✅ Acceptable |
| RS (Respond) | RS.MA, RS.AN, RS.CO, RS.MI | 0 | ✅ Acceptable |
| RC (Recover) | RC.RP, RC.CO | 0 | ✅ Acceptable |

---

## Compliance Coverage

| Framework | Coverage | Details |
|-----------|----------|---------|
| CWE | 4 unique CWEs identified | CWE-16, CWE-829, CWE-272, CWE-319 |
| SANS/CWE Top 25 | 0/25 entries found | None of this pass's findings match a Top 25 entry |
| OWASP ASVS 5.0 | 1/14 chapters with findings | V14 (Configuration/Deployment) |
| PCI DSS 4.0.1 | 2 requirements relevant | 4.2 |
| MITRE ATT&CK | 1 technique mapped | T1195 (Supply Chain Compromise), T1557 (Adversary-in-the-Middle) |
| SOC 2 | 2 criteria with findings | CC6.7, CC8.1 |
| ISO 27001:2022 | 3 controls with findings | A.8.9, A.8.24, A.8.29 |

---

## 🟢 Low & 🔵 Informational Findings

### 🟢 [LOW-001] Missing HSTS at the nginx layer — ✅ Permissions-Policy fixed
- **Severity**: 🟢 LOW
- **OWASP**: A02:2025 (Security Misconfiguration) | **CWE**: CWE-16 (Configuration) | **NIST CSF**: PR.PS (secondary PR.DS)
- **Compliance**: ASVS V14 | PCI DSS 4.2 | SOC 2 CC6.7 | ISO 27001 A.8.9
- **Location**: `frontend/nginx.conf:12-19`
- `Permissions-Policy` is now live (confirmed at `:15`). `Strict-Transport-Security` remains deliberately commented-out (`:19`) — this nginx only serves plain HTTP internally today; enabling HSTS before TLS termination exists in front of it would lock out legitimate access. Uncomment it the day a TLS-terminating layer is added, not before.
- **Current Code** (`frontend/nginx.conf:12-19`):
  ```nginx
  add_header X-Content-Type-Options nosniff always;
  add_header X-Frame-Options DENY always;
  add_header Referrer-Policy strict-origin-when-cross-origin always;
  add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
  add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://cdn.discordapp.com; font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'" always;
  # Uncomment once a TLS-terminating layer sits in front of this nginx (reverse proxy / load balancer) -
  # NOT before, since HSTS on a still-plain-HTTP deployment can lock browsers out of legitimate access:
  # add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
  ```
- **Remaining action**: None until TLS termination is introduced — then uncomment the HSTS line.

### 🟢 [LOW-002] `node:22-alpine` build-stage base image not pinned to a digest — deliberately not fixed
- **Severity**: 🟢 LOW
- **OWASP**: A03:2025 (Software Supply Chain Failures) | **CWE**: CWE-829 | **NIST CSF**: GV.SC
- **Compliance**: SOC 2 CC8.1 | ISO 27001 A.8.29
- **Location**: `frontend/Dockerfile.frontend:1`
- Build-stage image floats on the `22-alpine` tag (discarded before the final pinned `nginx:1.27-alpine` runtime image ships). Low impact — mitigated by `npm ci` enforcing the lockfile.
- **Decision**: Skipped on purpose — digest-pinning costs more in manual re-pin maintenance than the marginal supply-chain benefit for a build-only, discarded stage.

### 🟢 [LOW-003] CI workflow actions pinned to mutable tags, no explicit token permissions — ✅ Fixed
- **Severity**: 🟢 LOW
- **OWASP**: A03:2025 (Software Supply Chain Failures), secondary A02:2025 | **CWE**: CWE-829, CWE-272 (Least Privilege Violation) | **NIST CSF**: GV.SC, PR.PS
- **Compliance**: MITRE ATT&CK T1195 | SOC 2 CC8.1 | ISO 27001 A.8.29
- **Location**: `.github/workflows/backend-ci.yml:18-25`
- **Current Code** (confirmed live):
  ```yaml
  jobs:
    build:
      runs-on: ubuntu-latest
      permissions:
        contents: read

      steps:
        - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683 # v4.2.2

        - name: Set up JDK 21
          uses: actions/setup-java@c5195efecf7bdfc987ee8bae7a71cb8b11521c00 # v4.7.1
  ```
- **Remaining action**: None. Re-verify the pinned SHA whenever the workflow is next touched.

### 🟢 [LOW-004] Plaintext LDAP between backend and VPN sidecar over the internal docker network — ✅ Documented (no real fix exists)
- **Severity**: 🟢 LOW
- **OWASP**: A04:2025 (Cryptographic Failures) | **CWE**: CWE-319 (Cleartext Transmission of Sensitive Information) | **NIST CSF**: PR.DS
- **Compliance**: PCI DSS 4.2 | MITRE ATT&CK T1557 | ISO 27001 A.8.24
- **Location**: `infra/docker-compose.yml:93-98`
- LDAP simple-bind credentials transit the internal docker bridge network in cleartext between `backend` and `vpn`. Confined to the compose network (never host-exposed); the outer `vpn→ldap.stuba.sk` leg is inside the OpenVPN tunnel. No StartTLS/LDAPS available on STU's server, so no real fix exists — accepted risk, now documented in-line.
- **Current Code** (confirmed live):
  ```yaml
      # Plaintext ldap:// - STU's server doesn't offer StartTLS/LDAPS. Accepted risk: this hop is
      # confined to the internal compose network (never exposed on a host port), and the outer
      # vpn->ldap.stuba.sk leg is already inside the OpenVPN tunnel. Revisit if STU ever adds TLS.
      LDAP_URL: ldap://vpn:1389
      LDAP_BASE: ${LDAP_BASE:-ou=People,dc=stuba,dc=sk}
  ```
- **Remaining action**: None until STU's LDAP server gains TLS support.

---

## 📍 Security Hotspots

### [HOTSPOT-001] Authorization is 100% manual/per-controller with no declarative floor
- **OWASP**: A06:2025, secondary A01:2025 | **CWE**: CWE-862 (latent), CWE-284 | **NIST CSF**: ID.RA, GV.RM, PR.AA
- **Location**: `backend/src/main/java/sk/gkanocz/aisauth/config/SecurityConfig.java`; all 23 `@RestController` classes
- **Why sensitive**: No `@EnableMethodSecurity`/`@PreAuthorize` anywhere — every guild-scoped or admin check is a manual call to `guildAccessService.assertCanManageGuild(...)`/`assertSuperAdmin(...)` inside each controller method.
- **Risk if modified**: A future endpoint that forgets the one-line manual check is immediately open to any authenticated user, with nothing at compile time or route-config time to catch it.
- **Review guidance**: Any new controller method should be checked in review for a leading `guildAccessService` call.

### [HOTSPOT-002] `infra/vpn/entrypoint.sh` — VPN auth-file lifecycle
- **OWASP**: A08:2025, A02:2025 | **CWE**: CWE-269 | **NIST CSF**: GV.SC, PR.PS
- **Location**: `infra/vpn/entrypoint.sh:20-31`
- **Why sensitive**: This is the entire security model replacing the old `auth.txt` file — `umask 077` before writing the runtime credential file, `/run` mounted as `tmpfs` (memory-only), `trap cleanup EXIT INT TERM` removes it on any exit path.
- **Risk if modified**: Losing the `umask` line or the cleanup trap silently reintroduces a disk-persisted or world-readable credential file.
- **Review guidance**: Any edit to this script's write/cleanup logic needs explicit re-verification of both properties.

### [HOTSPOT-003] `infra/docker-compose.yml` — required-secret enforcement
- **OWASP**: A02:2025 | **CWE**: CWE-798 (latent) | **NIST CSF**: PR.PS
- **Location**: `infra/docker-compose.yml` (11 `${VAR:?must be set}` entries)
- **Why sensitive**: Sole enforcement point stopping a deploy from silently running with default/placeholder secrets.
- **Risk if modified**: Deleting a `:?...must be set` suffix "just to test locally" silently reintroduces an insecure default — nothing currently tests for this regression.
- **Review guidance**: Treat any diff touching these lines as security-relevant.

### [HOTSPOT-004] `frontend/src/lib/api.ts` — global 401/403 interceptor
- **OWASP**: A07:2025 | **CWE**: CWE-287 | **NIST CSF**: PR.AA
- **Location**: `frontend/src/lib/api.ts:8-30`
- **Why sensitive**: Drives session invalidation and forced logout/redirect for the whole SPA.
- **Risk if modified**: Could accidentally create a fail-open path (swallowing a 401 without redirecting) or reintroduce an infinite refresh loop.

### [HOTSPOT-005] `frontend/src/contexts/AuthContext.tsx` — OAuth code exchange
- **OWASP**: A07:2025 | **CWE**: CWE-287 | **NIST CSF**: PR.AA
- **Location**: `frontend/src/contexts/AuthContext.tsx:31-48`
- **Why sensitive**: Crosses the unauthenticated→authenticated trust boundary via the OAuth `code` query param.
- **Risk if modified**: Any future change must not weaken the httpOnly-cookie-only token model.

### [HOTSPOT-006] `frontend/nginx.conf` — single-line CSP
- **OWASP**: A05:2025 | **CWE**: CWE-79 (adjacent) | **NIST CSF**: PR.DS
- **Location**: `frontend/nginx.conf:17`
- **Why sensitive**: Easy to silently weaken (e.g. adding `'unsafe-inline'` to fix a dev annoyance) without anyone noticing until it's exploitable.

### [HOTSPOT-007] `frontend/Dockerfile.frontend` — `VITE_*` build-time bundle exposure
- **OWASP**: A04:2025 | **CWE**: CWE-200 | **NIST CSF**: PR.DS
- **Location**: `frontend/Dockerfile.frontend:11-12`
- **Why sensitive**: Vite bakes every `VITE_*` env var into the public JS bundle by convention.
- **Risk if modified**: Any future `VITE_*` variable introduced to carry a secret would leak it to every visitor's browser.

### [HOTSPOT-008] `backend/src/test/java/.../support/AuthenticatedRequestHelper.java` — test auth fixture
- **OWASP**: A06:2025 | **CWE**: N/A (test infrastructure) | **NIST CSF**: GV.RM
- **Location**: `backend/src/test/java/sk/gkanocz/aisauth/support/AuthenticatedRequestHelper.java`
- **Why sensitive**: New this pass. `SUPER_ADMIN_DISCORD_ID` here must stay in lockstep with any `@TestPropertySource(properties = "app.admin.super-admin-ids=...")` on every integration test using `superAdminToken()` — the class-level javadoc added this pass documents this coupling, but it's easy to add a new super-admin-gated test without the matching `@TestPropertySource` and get a confusing 403 instead of a clear failure.
- **Risk if modified**: A future test using `superAdminToken()` without the matching `@TestPropertySource` will fail with 403s that look like a product bug rather than a missing test-config wire-up.
- **Review guidance**: Any new `@SpringBootTest` class calling `auth.superAdminToken()` needs the `@TestPropertySource` line too.

---

## 🧹 Code Smells

### [SMELL-001] `frontend/src/lib/api.ts` is a 605-line flat endpoint aggregator
- **OWASP**: A06:2025 | **CWE**: N/A (maintainability) | **NIST CSF**: GV.RM
- **Location**: `frontend/src/lib/api.ts`
- **Pattern**: ~50 endpoint wrappers in one flat `adminApi` object, no feature-based module split.
- **Security implication**: Not a vulnerability, but makes it harder to audit which endpoints are auth-sensitive at a glance as the file grows.
- **Suggestion**: Split by domain (settings, modules, admin, semester) as a low-priority refactor. Mechanical, low-risk — no behavior change, just moves function groups into separate files importing the shared `api` instance.

### [SMELL-002] Guild-selection `localStorage` state duplicated across three files
- **OWASP**: A06:2025 | **CWE**: N/A (maintainability) | **NIST CSF**: GV.RM
- **Location**: `frontend/src/components/layout/Layout.tsx:60`, `frontend/src/components/modules/shared.tsx:16-27`, `frontend/src/pages/SelectServer.tsx:21-35`
- **Pattern**: Independent `localStorage` reads/writes instead of funneling entirely through the shared `useSelectedGuildId`/`setSelectedGuildId` helpers already exported from `shared.tsx`.
- **Security implication**: Minor drift risk — a future edit forgetting to dispatch the `guild-changed` event leaves other components' state stale. Not a security bug today.
- **Suggestion**: Route all three through the shared helper; drop the redundant local constants.

---

## Recommendations Summary

All Low-severity, 3 of 4 already fixed:

1. ✅ Done — `Permissions-Policy` added; HSTS staged for when TLS lands (LOW-001).
2. ➖ Deliberately skipped — `node:22-alpine` digest pin, cost > benefit (LOW-002).
3. ✅ Done — CI actions SHA-pinned, `permissions: contents: read` added (LOW-003).
4. ✅ Done — LDAP cleartext hop documented as accepted risk, no real fix exists (LOW-004).

Remaining open, none urgent: `@PreAuthorize` default-deny safety net (HOTSPOT-001), CI dependency/SCA scanning (previously noted, still open), SMELL-001/002 refactors.

---

## Methodology

| Aspect | Details |
|--------|---------|
| Phases executed | 1–5 in the prior full pass this session (exhaustive); this pass verified the diff since then (`git diff a0a2948..HEAD`) was test-fixtures only and confirmed the 3 applied fixes are genuinely live in the affected files, rather than re-running a redundant full sweep against an unchanged production/infra/frontend codebase |
| Frameworks detected | Spring Boot 4.1.0, Spring Security, OAuth2 client + resource server, Spring Data JPA + Flyway/PostgreSQL, Spring Data LDAP, jjwt, JDA 5.6.1, React + TypeScript + Vite, nginx, Docker Compose, OpenVPN sidecar |
| White-box categories | All 20 categories checked in the prior full pass; no production/infra/frontend code changed since, so findings carry forward unchanged |
| Gray-box testing | Authorization consistency verified across all 23 backend REST controllers and all 11 Discord bot listeners in the prior pass — no gaps found, unchanged this pass |
| Security hotspots | 8 flagged (7 carried forward + 1 new: the test auth-fixture coupling introduced by this session's super-admin live-check fix) |
| Code smells | 2 flagged, unchanged from the prior pass |
| Packs loaded | none |
| Scope exclusions | no `.security-audit-ignore` present |
| Baseline comparison | no `.security-audit-baseline.json` present |
| OWASP Top 10:2025 | 3/10 categories with (Low-only) findings — A02, A03, A04; all others clean |
| NIST CSF 2.0 | GV, PR touched by Low findings; ID, DE, RS, RC clean |
| CWE | 4 unique CWE IDs identified |
| SANS/CWE Top 25 | 0/25 matched |
| ASVS 5.0 | 1 chapter checked with findings (V14) |
| Additional frameworks | PCI DSS 4.0.1, MITRE ATT&CK, SOC 2, ISO 27001:2022 |

**Re-verified clean, no regressions** (carried forward from the prior exhaustive pass, unchanged since — production/infra/frontend diff since then was empty): PII leak in `/find` Discord command; stale super-admin JWT claim; unvalidated `guildId` from raw request bodies; missing `DB_PASSWORD`/`JWT_SECRET` fail-fast checks; the committed OpenVPN `tls-auth` key (confirmed non-secret); weak default DB/Mailpit exposure; backend port publicly exposed; `auth.txt` file-based VPN credentials; unauthenticated deserialization in `CookieOAuth2AuthorizationRequestRepository`; Discord admin-only command floor on `/manualverify`/`/warn`; all 23 REST controllers' authorization consistency; all 11 Discord bot listeners; JWT sign/verify + session revocation; OAuth2 state validation; LDAP/JPA query construction; exception handling; logging; token/secret generation; mass assignment; deserialization/dynamic code execution; Docker/CI infrastructure; frontend XSS surface; token storage; open redirects; secrets in client bundle; dependency provenance; CSP/security headers; nginx `X-Forwarded-For` handling; docker-compose network exposure; `.gitignore`/secret tracking.

**Newly verified this pass**: `frontend/nginx.conf`, `.github/workflows/backend-ci.yml`, `infra/docker-compose.yml` hand-read to confirm the 3 claimed Low fixes are genuinely present (not just claimed in a prior report); full `git diff a0a2948..HEAD -- backend/src/test` hand-reviewed line-by-line — confirmed 7 files, all test-fixture corrections (stale super-admin ID config, non-snowflake test guildIds, a stale email-in-reply assertion), zero assertions weakened, one assertion strengthened.

---

*Report generated by Claude Security Audit*
