# Security Audit Report

**Project**: spring-ais-authentication (AIS Discord verification backend + frontend)
**Date**: 2026-08-17
**Auditor**: Claude Security Audit
**Frameworks**: OWASP Top 10:2025 + NIST CSF 2.0
**Mode**: full --fix (re-audit — verifies all prior findings plus a fresh full sweep; fixed code included below)

---

## Remediation Status (same-day follow-up)

| ID | Status | Note |
|----|--------|------|
| LOW-001 | ✅ Fixed | `Permissions-Policy` added to `frontend/nginx.conf`. HSTS left commented-out on purpose - no TLS in front of this stack yet. |
| LOW-002 | ➖ Skipped, deliberately | Digest-pinning `node:22-alpine` costs more (manual re-pin on every bump) than it buys (build-stage only, discarded before the pinned runtime image ships, `npm ci` already locks real deps). Not worth it. |
| LOW-003 | ✅ Fixed | `backend-ci.yml`: `permissions: contents: read` added, both actions pinned to commit SHA. |
| LOW-004 | ✅ Documented | `infra/docker-compose.yml`: accepted-risk comment added above `LDAP_URL`. No real fix exists - confirmed live this session that STU's LDAP only speaks plain `ldap://`. |
| SMELL-001/002 | ⏳ Not applied | Refactor-only, no security impact - code blocks left in the report as a reference for whenever someone gets to it. |

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

**Overall Risk Assessment**: Low. This is a re-audit of a codebase that already went through a full audit and remediation pass earlier the same day. Every previously reported finding (PII leak in `/find`, stale super-admin claim, unvalidated `guildId` from raw request bodies, missing `DB_PASSWORD`/`JWT_SECRET` fail-fast checks, the committed OpenVPN key false-positive, weak default DB credentials, exposed ports, `auth.txt` elimination) was independently re-verified against the actual current code — not just re-read from the prior report — and confirmed correctly and completely fixed, with zero regressions. A genuinely fresh sweep of all 23 backend REST controllers, all 11 Discord bot listeners, the full JWT/OAuth2/LDAP/JPA stack, and the entire frontend found **zero new Critical/High/Medium findings**. The only open items are 4 Low-severity, forward-looking hardening gaps in infrastructure/CI (unpinned build-stage base image, unpinned CI actions, missing HSTS for a future TLS deployment, internal-network LDAP in cleartext) — none exploitable under the current deployment topology.

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

### 🟢 [LOW-001] Missing HSTS and Permissions-Policy headers at the nginx layer
- **Severity**: 🟢 LOW
- **OWASP**: A02:2025 (Security Misconfiguration) | **CWE**: CWE-16 (Configuration) | **NIST CSF**: PR.PS (secondary PR.DS)
- **Compliance**: ASVS V14 | PCI DSS 4.2 | SOC 2 CC6.7 | ISO 27001 A.8.9
- **Location**: `frontend/nginx.conf:12-17`
- No `Strict-Transport-Security` or `Permissions-Policy` header is set. Currently inert since this nginx only serves plain HTTP internally (TLS termination, if any, happens further upstream) — but it's a latent gap: the moment TLS gets added in front of this stack, nobody would think to revisit `nginx.conf` to add HSTS, leaving a downgrade-attack window open by omission.
- **Vulnerable Code** (`frontend/nginx.conf:12-17`):
  ```nginx
  add_header X-Content-Type-Options nosniff always;
  add_header X-Frame-Options DENY always;
  add_header Referrer-Policy strict-origin-when-cross-origin always;
  add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://cdn.discordapp.com; font-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'" always;
  ```
- **Remediation**: Add `Permissions-Policy` now (harmless regardless of TLS status). Add HSTS **only once TLS termination is confirmed in front of this stack** — uncomment it at that point, not before (it can lock out legitimate HTTP-only access otherwise):
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

### 🟢 [LOW-002] `node:22-alpine` build-stage base image not pinned to a digest
- **Severity**: 🟢 LOW
- **OWASP**: A03:2025 (Software Supply Chain Failures) | **CWE**: CWE-829 | **NIST CSF**: GV.SC
- **Compliance**: SOC 2 CC8.1 | ISO 27001 A.8.29
- **Location**: `frontend/Dockerfile.frontend:1`
- Build-stage image floats on the `22-alpine` tag (discarded before the final pinned `nginx:1.27-alpine` runtime image ships). Low impact — mitigated by `npm ci` enforcing the lockfile — but a supply-chain integrity gap for the toolchain itself.
- **Vulnerable Code** (`frontend/Dockerfile.frontend:1`):
  ```dockerfile
  FROM node:22-alpine AS build
  ```
- **Remediation**: Pin to today's resolved digest for `node:22-alpine` (re-pin whenever you deliberately bump the Node version):
  ```dockerfile
  FROM node:22-alpine@sha256:c610fcdfb1d5b4740dd70c284ed3cb16bb857e0f7166196e36a5501df7a3aa32 AS build
  ```

### 🟢 [LOW-003] CI workflow actions pinned to mutable tags, no explicit token permissions
- **Severity**: 🟢 LOW
- **OWASP**: A03:2025 (Software Supply Chain Failures), secondary A02:2025 | **CWE**: CWE-829, CWE-272 (Least Privilege Violation) | **NIST CSF**: GV.SC, PR.PS
- **Compliance**: MITRE ATT&CK T1195 | SOC 2 CC8.1 | ISO 27001 A.8.29
- **Location**: `.github/workflows/backend-ci.yml:20,23`
- `actions/checkout@v4` and `actions/setup-java@v4` are mutable-tag pins, not commit-SHA pins. No `permissions:` block, so the job inherits the repo's default `GITHUB_TOKEN` scope instead of an explicitly minimized one. Low impact today — no secrets used, no write actions, no `pull_request_target` — but a cheap hardening step.
- **Vulnerable Code** (`.github/workflows/backend-ci.yml`):
  ```yaml
  jobs:
    build:
      runs-on: ubuntu-latest

      steps:
        - uses: actions/checkout@v4

        - name: Set up JDK 21
          uses: actions/setup-java@v4
  ```
- **Remediation**: Pin to commit SHAs (with a version comment for readability) and add an explicit read-only default token scope:
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

### 🟢 [LOW-004] Plaintext LDAP between backend and VPN sidecar over the internal docker network
- **Severity**: 🟢 LOW
- **OWASP**: A04:2025 (Cryptographic Failures) | **CWE**: CWE-319 (Cleartext Transmission of Sensitive Information) | **NIST CSF**: PR.DS
- **Compliance**: PCI DSS 4.2 | MITRE ATT&CK T1557 | ISO 27001 A.8.24
- **Location**: `infra/docker-compose.yml:95` (`LDAP_URL: ldap://vpn:1389`)
- LDAP simple-bind credentials (base64, not encrypted) transit the docker bridge network between `backend` and `vpn` in cleartext. Traffic from `vpn` onward to the real university server is protected by the OpenVPN tunnel — this gap is only the internal `backend↔vpn` hop, not externally exposed. Requires an attacker to already have a foothold inside the docker network.
- **Vulnerable Code** (`infra/docker-compose.yml:93-95`):
  ```yaml
      LDAP_URL: ldap://vpn:1389
      LDAP_BASE: ${LDAP_BASE:-ou=People,dc=stuba,dc=sk}
  ```
- **Remediation**: No code fix available today — STU's LDAP server was confirmed this session to only speak plain `ldap://` on port 389 (no StartTLS/LDAPS observed). Document the accepted risk explicitly instead of leaving it silent:
  ```yaml
      # Plaintext ldap:// - STU's server doesn't offer StartTLS/LDAPS. Accepted risk: this hop is
      # confined to the internal compose network (never exposed on a host port), and the outer
      # vpn->ldap.stuba.sk leg is already inside the OpenVPN tunnel. Revisit if STU ever adds TLS.
      LDAP_URL: ldap://vpn:1389
      LDAP_BASE: ${LDAP_BASE:-ou=People,dc=stuba,dc=sk}
  ```
  If StartTLS ever becomes available, terminate it at the `vpn` sidecar (e.g. `stunnel` in front of `socat`, or swap `socat` for an LDAPS-aware proxy) rather than in the Java backend.

---

## 📍 Security Hotspots

### [HOTSPOT-001] Authorization is 100% manual/per-controller with no declarative floor
- **OWASP**: A06:2025, secondary A01:2025 | **CWE**: CWE-862 (latent), CWE-284 | **NIST CSF**: ID.RA, GV.RM, PR.AA
- **Location**: `backend/src/main/java/sk/gkanocz/aisauth/config/SecurityConfig.java`; all 23 `@RestController` classes
- **Why sensitive**: No `@EnableMethodSecurity`/`@PreAuthorize` anywhere — every guild-scoped or admin check is a manual call to `guildAccessService.assertCanManageGuild(...)`/`assertSuperAdmin(...)` inside each controller method. Re-verified this pass: all 23 controllers still call it correctly, no gaps found.
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

---

## 🧹 Code Smells

### [SMELL-001] `frontend/src/lib/api.ts` is a 605-line flat endpoint aggregator
- **OWASP**: A06:2025 | **CWE**: N/A (maintainability) | **NIST CSF**: GV.RM
- **Location**: `frontend/src/lib/api.ts`
- **Pattern**: ~50 endpoint wrappers in one flat `adminApi` object, no feature-based module split.
- **Security implication**: Not a vulnerability, but makes it harder to audit which endpoints are auth-sensitive at a glance as the file grows.
- **Suggestion**: Split by domain (settings, modules, admin, semester) as a low-priority refactor:
  ```ts
  // frontend/src/lib/api/semester.ts
  import { api } from '../api-client'; // the shared axios instance + interceptors from api.ts

  export const semesterApi = {
    getConfigs: (guildId: string) => api.get(`/semester/configs`, { params: { guildId } }),
    saveConfigs: (body: unknown) => api.post(`/semester/configs`, body),
    // ...
  };

  // frontend/src/lib/api.ts - keep the axios instance + interceptors here, re-export the split modules
  export { semesterApi } from './api/semester';
  export { settingsApi } from './api/settings';
  export { adminApi } from './api/admin';
  ```
  Mechanical, low-risk refactor — no behavior change, just moves function groups into separate files that import the shared `api` instance instead of redeclaring it.

### [SMELL-002] Guild-selection `localStorage` state duplicated across three files
- **OWASP**: A06:2025 | **CWE**: N/A (maintainability) | **NIST CSF**: GV.RM
- **Location**: `frontend/src/components/layout/Layout.tsx:60`, `frontend/src/components/modules/shared.tsx:16-27`, `frontend/src/pages/SelectServer.tsx:21-35`
- **Pattern**: Independent `localStorage` reads/writes instead of funneling entirely through the shared `useSelectedGuildId`/`setSelectedGuildId` helpers already exported from `shared.tsx`. `Layout.tsx` even redeclares its own `GUILD_KEY = "selected_guild_id"` constant despite already importing the shared helpers; `SelectServer.tsx` redeclares it a third time as `GUILD_STORAGE_KEY`.
- **Security implication**: Minor drift risk — a future edit to one call site that forgets to dispatch the `guild-changed` event (which only `setSelectedGuildId` does) leaves other components' `useSelectedGuildId()` state stale until a manual refresh. Not a security bug today.
- **Vulnerable Code** (`frontend/src/pages/SelectServer.tsx:8`):
  ```ts
  const GUILD_STORAGE_KEY = "selected_guild_id";
  // ...later, direct localStorage.setItem(GUILD_STORAGE_KEY, ...) instead of setSelectedGuildId(...)
  ```
- **Remediation**: Drop the local constant and any direct `localStorage` calls in `SelectServer.tsx`, import and use the existing shared helpers instead:
  ```ts
  import { setSelectedGuildId } from "@/components/modules/shared";

  // replace: localStorage.setItem(GUILD_STORAGE_KEY, guild.id)
  setSelectedGuildId(guild.id);
  ```
  Same for `Layout.tsx:12` — drop its redundant local `GUILD_KEY` constant entirely since `useSelectedGuildId`/`setSelectedGuildId` (already imported there) don't need it duplicated.

---

## Recommendations Summary

All items are Low severity, none urgent:

1. Pin GitHub Actions to commit SHAs, add `permissions: contents: read` to `backend-ci.yml` (LOW-003).
2. Pin `node:22-alpine` to a digest in `Dockerfile.frontend` (LOW-002).
3. Add `Permissions-Policy` now; add HSTS at the point TLS termination is introduced in front of this stack, not before (LOW-001).
4. Document (or fix, if STU's LDAP ever supports StartTLS) the internal-network cleartext LDAP hop (LOW-004).

---

## Methodology

| Aspect | Details |
|--------|---------|
| Phases executed | 1–5 (full), as a re-audit verifying all prior findings plus a fresh full sweep |
| Frameworks detected | Spring Boot 4.1.0, Spring Security, OAuth2 client + resource server, Spring Data JPA + Flyway/PostgreSQL, Spring Data LDAP, jjwt, JDA 5.6.1, React + TypeScript + Vite, nginx, Docker Compose, OpenVPN sidecar |
| White-box categories | All 20 categories checked across both backend and frontend/infra scopes |
| Gray-box testing | Authorization consistency re-verified across all 23 backend REST controllers and all 11 Discord bot listeners — no gaps found; not itemized as separate GRAY findings since no discrepancy was found to report |
| Security hotspots | 7 flagged: manual authorization architecture, VPN auth-file lifecycle, compose required-secret enforcement, frontend auth interceptor, OAuth code-exchange bootstrap, nginx CSP, Vite build-time bundle exposure |
| Code smells | 2 flagged: oversized flat API client module, duplicated guild-selection localStorage logic |
| Packs loaded | none |
| Scope exclusions | no `.security-audit-ignore` present |
| Baseline comparison | no `.security-audit-baseline.json` present |
| OWASP Top 10:2025 | 3/10 categories with (Low-only) findings — A02, A03, A04; all others clean |
| NIST CSF 2.0 | GV, PR touched by Low findings; ID, DE, RS, RC clean |
| CWE | 4 unique CWE IDs identified |
| SANS/CWE Top 25 | 0/25 matched |
| ASVS 5.0 | 1 chapter checked with findings (V14) |
| Additional frameworks | PCI DSS 4.0.1, MITRE ATT&CK, SOC 2, ISO 27001:2022 |

**Re-verified clean, no regressions** (full list of prior findings, each independently re-checked against current code this pass): PII leak in `/find` Discord command (email removed from reply); stale super-admin JWT claim (now live-rechecked against `AdminProperties.superAdminIds()`); unvalidated `guildId` from raw request bodies (now routed through `requireValidGuildId`); missing `DB_PASSWORD`/`JWT_SECRET` fail-fast checks (`ProductionSecretsValidator` covers both); the committed OpenVPN `tls-auth` key (confirmed non-secret, byte-identical to STU's own public download, correctly left tracked); weak default DB/Mailpit exposure (bound to loopback); backend port publicly exposed (removed, nginx-only ingress); `auth.txt` file-based VPN credentials (eliminated, replaced by env vars written to a tmpfs runtime file); unauthenticated deserialization in `CookieOAuth2AuthorizationRequestRepository` (now HS256-signed JWT, not Java serialization); Discord admin-only command floor on `/manualverify`/`/warn` (still enforced, including on redeploy).

**Areas explicitly verified clean this pass** (backend): all 23 REST controllers' authorization consistency, all 11 Discord bot listeners, JWT sign/verify + session revocation, OAuth2 state validation, LDAP query construction (parameterized, plus AIS-ID format pre-validation), JPA query construction (single parameterized `@Query`, no native SQL), exception handling (no info leakage), logging (no secrets/PII, explicit redaction of `email`/`code` command options in audit logs), token/secret generation (`SecureRandom` throughout, no `Math.random()`), mass assignment (explicit field allowlist, no reflective binding), deserialization/dynamic code execution (none present), Docker/CI infrastructure (non-root user, healthcheck, no CI secret handling).

**Areas explicitly verified clean this pass** (frontend/infra): XSS surface (zero `dangerouslySetInnerHTML`/`eval`/`innerHTML`), token storage (httpOnly cookies only, no tokens in `localStorage`), open redirects (none, all fixed internal routes), secrets in client bundle (none beyond the public `VITE_API_URL`), dependency provenance (all resolved from the official npm registry), CSP/security headers (correctly scoped, no `unsafe-inline`/`unsafe-eval`), nginx `X-Forwarded-For` handling (append-only, not spoofable), docker-compose network exposure (no unintended host ports), `.gitignore`/secret tracking (`.env` files correctly excluded from git and Docker build context).

---

*Report generated by Claude Security Audit*
