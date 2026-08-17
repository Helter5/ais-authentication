# Security Audit Report

**Project**: spring-ais-authentication (AIS Discord verification backend + frontend)
**Date**: 2026-08-17
**Auditor**: Claude Security Audit
**Frameworks**: OWASP Top 10:2025 + NIST CSF 2.0
**Mode**: full

---

## Executive Summary

| Metric | Count |
|--------|-------|
| 🔴 Critical | 0 |
| 🟠 High | 0 |
| 🟡 Medium | 4 |
| 🟢 Low | 5 |
| 🔵 Informational | 5 |
| 🔲 Gray-box findings | 2 |
| 📍 Security hotspots | 4 |
| 🧹 Code smells | 3 |
| **Total findings** | **14** (1 downgraded to Info after verification, see Remediation Status) |

**Overall Risk Assessment**: Low. No critical or high-severity vulnerability was found — access control, JWT handling, LDAP/JPA query construction, OAuth2 state validation, CORS, and exception handling are all sound, and a prior remediation PR (`0af3a79`) already closed the cookie-flag, JWT-fail-fast, CSP, rate-limit and dependency-CVE gaps identified in the previous audit. All Medium findings from the initial pass are now fixed except MEDIUM-002, and one initial Medium finding (the committed OpenVPN `tls-auth` key) was verified to be the university's own publicly-distributed config, not a secret — downgraded to informational, no action needed. Remaining open item: the super-admin JWT claim isn't re-validated live the way manager roles are (MEDIUM-002) — fix in progress.

---

## Remediation Status (as of 2026-08-17, same-day follow-up)

| ID | Status | Note |
|----|--------|------|
| MEDIUM-001 (`/find`/`/user` PII leak) | ✅ Partially fixed | `/find` no longer includes email in its reply. `/user` left as-is per product decision — guild admins are expected to restrict it via the existing per-guild `CommandPermissions` dashboard setting, not a code-level default. |
| MEDIUM-002 (stale super-admin claim) | ✅ Fixed | `GuildAccessService.isSuperAdmin()` now also cross-checks `AdminProperties.superAdminIds()` (SUPER_ADMIN_IDS) live on every call, not just the JWT claim — an ID removed from config and redeployed loses super-admin access on its very next request. |
| MEDIUM-003 (`tls-auth` key in git) | 🔵 Downgraded to Info, resolved | Verified via SHA-256 comparison against STU's own public download that the `<ca>`/`<tls-auth>` blocks are the university's standard published config, not a secret. No rotation or history rewrite needed. Repo hygiene (untrack + `.example`) from the earlier pass left in place as harmless but no longer necessary. |
| MEDIUM-004 (weak default DB password + published port) | ✅ Fixed | `ProductionSecretsValidator` now also rejects the placeholder DB password under the `prod` profile; `infra/docker-compose.yml` binds Postgres to `127.0.0.1` instead of all interfaces. |
| MEDIUM-005 (Mailpit SMTP exposed) | ✅ Fixed | Bound to `127.0.0.1:1025:1025`. |
| LOW-001 (`guildId` validation) | ✅ Fixed | `GuildAccessService.requireValidGuildId()` added; both flagged endpoints now validate before use. |
| LOW-002 (`nginx:alpine` unpinned) | ✅ Fixed | Pinned to `nginx:1.27-alpine`. |
| LOW-003 (VPN capabilities) | ➖ No change needed | Confirmed minimum necessary set. |
| LOW-004 / SMELL-002 (unused `jwt-decode`) | ✅ Fixed | Removed via `npm uninstall`; `npm audit fix` also applied while touching the lockfile (4 of 5 unrelated dev-tooling advisories resolved, 1 low-severity `esbuild` dev-server-only issue remains, not exploitable outside local dev). |
| LOW-005 (backend port bypasses nginx) | ✅ Fixed | `8080:8080` host publish removed from `infra/docker-compose.yml`. |
| INFO-003 (backend Docker digest pinning) | ⏳ Not done | Needs a live registry digest lookup; left as follow-up. |
| INFO-004 (CI SCA scanning) | ⏳ Not done | Left as follow-up - adding `dependency-check` needs an NVD API key / CI secret and wasn't in scope for this pass. |

---

## OWASP Top 10:2025 Coverage

| OWASP ID | Category | Findings | Status |
|----------|----------|----------|--------|
| A01:2025 | Broken Access Control | 1 | 🔴 Needs Attention |
| A02:2025 | Security Misconfiguration | 4 | 🔴 Needs Attention |
| A03:2025 | Software Supply Chain Failures | 4 | 🔴 Needs Attention |
| A04:2025 | Cryptographic Failures | 2 | 🔴 Needs Attention |
| A05:2025 | Injection | 0 | ✅ Acceptable |
| A06:2025 | Insecure Design | 3 | 🔴 Needs Attention |
| A07:2025 | Authentication Failures | 3 | 🔴 Needs Attention |
| A08:2025 | Software or Data Integrity Failures | 1 | 🔴 Needs Attention |
| A09:2025 | Security Logging and Alerting Failures | 0 | ✅ Acceptable |
| A10:2025 | Mishandling of Exceptional Conditions | 0 | ✅ Acceptable |

---

## NIST CSF 2.0 Coverage

| Function | Categories | Findings | Status |
|----------|-----------|----------|--------|
| GV (Govern) | GV.RM, GV.SC | 5 | 🔴 Needs Attention |
| ID (Identify) | ID.RA | 1 | 🔴 Needs Attention |
| PR (Protect) | PR.AA, PR.DS, PR.PS | 12 | 🔴 Needs Attention |
| DE (Detect) | DE.CM, DE.AE | 0 | ✅ Acceptable |
| RS (Respond) | RS.MA, RS.AN, RS.CO, RS.MI | 0 | ✅ Acceptable |
| RC (Recover) | RC.RP, RC.CO | 0 | ✅ Acceptable |

---

## Compliance Coverage

| Framework | Coverage | Details |
|-----------|----------|---------|
| CWE | 15 unique CWEs identified | CWE-862, CWE-200, CWE-613, CWE-863, CWE-284, CWE-20, CWE-829, CWE-1104, CWE-798, CWE-321, CWE-260, CWE-16, CWE-269, CWE-319, CWE-79 (adjacent) |
| SANS/CWE Top 25 | 2/25 entries found | #9 (CWE-862 Missing Authorization), #17 (CWE-200 Exposure of Sensitive Information) |
| OWASP ASVS 5.0 | 7/14 chapters with findings | V2 (Authentication), V3 (Session Management), V4 (Access Control), V5 (Validation), V6 (Cryptography/Secrets), V8 (Data Protection), V14 (Config/Deployment) |
| PCI DSS 4.0.1 | 7 requirements relevant | 2.2, 3.5, 6.2.4, 6.3, 7.2–7.3, 8.3, 11.3 |
| MITRE ATT&CK | 3 techniques mapped | T1530 (Data from Cloud/Info Storage Object), T1552.001 (Credentials In Files), T1078.001 (Valid Accounts: Default Accounts) |
| SOC 2 | 5 criteria with findings | CC6.1, CC6.3, CC6.6, CC6.7, CC8.1 |
| ISO 27001:2022 | 9 controls with findings | A.5.15, A.8.2, A.8.3, A.8.8, A.8.9, A.8.11, A.8.24, A.8.26, A.8.29 |

---

## 🟡 Medium Findings

### 🟡 [MEDIUM-001] `/find` and `/user` Discord slash commands leak member email addresses to any guild member
- **Severity**: 🟡 MEDIUM
- **OWASP**: A01:2025 (Broken Access Control), secondary A04:2025
- **CWE**: CWE-862 (Missing Authorization), CWE-200 (Exposure of Sensitive Information)
- **NIST CSF**: PR.AA (primary), PR.DS (secondary)
- **Compliance**: SANS Top 25 #9, #17 | ASVS V4, V8 | PCI DSS 7.2–7.3 | T1530 | SOC 2 CC6.1 | ISO 27001 A.5.15, A.8.11
- **Location**: `backend/src/main/java/sk/gkanocz/aisauth/discordbot/DiscordBotService.java:121-151`, `backend/src/main/java/sk/gkanocz/aisauth/discordbot/VerificationSlashCommandListener.java:202-219`, `backend/src/main/java/sk/gkanocz/aisauth/discordbot/UtilityCommandListener.java:109-197`
- **Attack Vector**: Unlike `/manualverify` and `/warn`, which are locked with `DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)` at command-definition time, `/find` and `/user` carry no such default and are reachable by any guild member unless a super admin opts in to a per-guild `CommandPermissions` restriction (which defaults to `CommandPermissions.empty()`, i.e. open). Any member can run `/find ais_id:<guess>` and get back the matching user's Discord ID and real email; AIS/student IDs are often sequential, enabling enumeration/harvesting.
- **Vulnerable Code**:
  ```java
  String message = "**AIS ID:** " + user.getAisId()
          + "\n**Discord:** <@" + user.getDiscordId() + ">"
          + "\n**Email:** " + user.getEmail() + ...
  ```
- **Impact**: Bulk email/PII harvesting of verified members by any guild member, with no rate limit specific to `/find`.
- **Remediation**: Default `/find` and `/user` to admin-only via `DefaultMemberPermissions` (matching `/manualverify`/`/warn`), or strip the email field from replies unless the caller is a moderator.

### 🟡 [MEDIUM-002] Super-admin JWT claim not re-validated live, unlike manager roles
- **Severity**: 🟡 MEDIUM
- **OWASP**: A07:2025 (Authentication Failures)
- **CWE**: CWE-613 (Insufficient Session Expiration), CWE-863 (Incorrect Authorization)
- **NIST CSF**: PR.AA (primary), PR.DS (secondary)
- **Compliance**: ASVS V3 | SOC 2 CC6.3 | ISO 27001 A.8.2
- **Location**: `backend/src/main/java/sk/gkanocz/aisauth/auth/GuildAccessService.java:21-23` vs. `:53-71`; `backend/src/main/java/sk/gkanocz/aisauth/auth/AuthController.java:90-99`
- **Attack Vector**: `GuildAccessService.isSuperAdmin(Claims)` trusts the JWT's `superAdmin` boolean claim outright. Manager-role access is deliberately re-checked live against JDA's member cache on every request (per the class's own javadoc, so a Discord-side role revocation takes effect immediately) — super-admin status gets no equivalent check, and is only recomputed at `/api/auth/refresh`. If an operator removes an ID from `SUPER_ADMIN_IDS` and redeploys, an already-issued access token continues granting super-admin access — including destructive actions like `/api/wipe` — for up to its full TTL (default 300s).
- **Vulnerable Code**:
  ```java
  public boolean isSuperAdmin(Claims claims) {
      return Boolean.TRUE.equals(claims.get("superAdmin", Boolean.class));
  }
  ```
- **Impact**: A revoked super-admin can retain destructive access (guild wipe, allowed-guilds management) for up to the access-token TTL after revocation.
- **Remediation**: Re-validate `superAdmin` against `AdminProperties.superAdminIds()` on every privileged request (same pattern already used for manager roles), or provide an explicit session-revocation path tied to config changes.

### ~~🟡 [MEDIUM-003]~~ 🔵 [MEDIUM-003 → INFO, RESOLVED] OpenVPN `tls-auth` key — verified NOT a secret, false positive
- **Severity**: ~~🟡 MEDIUM~~ 🔵 INFO (downgraded after verification)
- **OWASP**: A02:2025 (Security Misconfiguration), secondary A04:2025 (Cryptographic Failures)
- **CWE**: CWE-798 (Use of Hard-coded Credentials), CWE-321 (Use of Hard-coded Cryptographic Key) — both N/A, see below
- **NIST CSF**: PR.DS (primary), PR.PS
- **Compliance**: ASVS V6.4 | PCI DSS 3.5 | T1552.001 | SOC 2 CC6.7 | ISO 27001 A.8.24
- **Location**: `infra/vpn/client.ovpn`
- **Verification (2026-08-17, follow-up)**: The developer identified that STU Bratislava publishes this exact `client.ovpn` — `<ca>` cert and `<tls-auth>` static key included — as the standard, identical-for-everyone connection profile on its own public IT support site (`stuba.sk/.../openvpn-v.3-connect.html`, direct download `stuba.sk/buxus/docs/stu/pracoviska/cvt/navody/client.ovpn`). Fetched that URL and SHA-256-compared its `<ca>` and `<tls-auth>` blocks byte-for-byte against the repo's copy: **identical hashes on both blocks**. This is not a per-installation or per-user secret — it's the university's own published default, distributed to every student/staff VPN user. The only actual secret in this flow is the personal `auth-user-pass` credential in `infra/vpn/auth.txt`, which was correctly gitignored from the start and was never affected by this finding.
- **Original attack vector reasoning (now moot)**: The `<tls-auth>` block was assumed to be a shared-but-not-public HMAC secret providing defense-in-depth against port scanning/DoS/replay. That assumption was wrong — STU treats it as public configuration, not a secret, so there is nothing to rotate and no exposure from this repo having tracked it.
- **Action taken anyway**: `infra/vpn/client.ovpn` was untracked and gitignored, with a redacted `client.ovpn.example` added, before this was confirmed (see commit `2a8bdbe`) — harmless, but unnecessary. Git history was **not** rewritten, and per this verification, does not need to be.
- **Remediation**: None required. Optional: re-track `client.ovpn` directly instead of via the `.example` indirection, since it's genuinely public config — left as-is to avoid unnecessary churn.

### 🟡 [MEDIUM-004] Weak default database credentials combined with published Postgres port
- **Severity**: 🟡 MEDIUM
- **OWASP**: A02:2025 (Security Misconfiguration)
- **CWE**: CWE-798 (Use of Default Credentials), CWE-260 (Password in Configuration File)
- **NIST CSF**: PR.PS (primary), PR.AA
- **Compliance**: ASVS V2.1 | PCI DSS 2.2, 8.3 | T1078.001 | SOC 2 CC6.1 | ISO 27001 A.8.9
- **Location**: `infra/docker-compose.yml:6-11,96`
- **Attack Vector**: `POSTGRES_PASSWORD: ${DB_PASSWORD:-ais_auth}` falls back to a trivially-guessable password if `.env` is missing (e.g. a rushed staging deploy), and `ports: ["5432:5432"]` publishes Postgres to the host's network interface, not just the internal compose network. Anyone reaching the host (misconfigured firewall/security group, same LAN) can `psql -h <host> -U ais_auth ais_auth` directly. Unlike `JWT_SECRET`, which `ProductionSecretsValidator` explicitly checks for the placeholder value and fails startup on, `DB_PASSWORD`/`DB_USER`/`DB_NAME` are not covered by that validator at all — this default silently succeeds even under the `prod` profile.
- **Impact**: Full read/write access to the application database (users, verification records, audit/session data) bypassing the application layer entirely.
- **Remediation**: Require `DB_PASSWORD` (`${DB_PASSWORD:?DB_PASSWORD must be set}`), extend `ProductionSecretsValidator` to reject a default/blank DB password under `prod`, and drop or restrict the `5432:5432` host port publish (bind `127.0.0.1:5432:5432` at most — `backend` already reaches `postgres` over the internal compose network).

### 🟡 [MEDIUM-005] Mailpit SMTP port published to host network, unauthenticated
- **Severity**: 🟡 MEDIUM
- **OWASP**: A02:2025 (Security Misconfiguration)
- **CWE**: CWE-16 (Configuration)
- **NIST CSF**: PR.PS
- **Compliance**: ASVS V14.1 | SOC 2 CC6.6 | ISO 27001 A.8.9
- **Location**: `infra/docker-compose.yml:20-26`
- **Attack Vector**: `ports: ["1025:1025"]` binds to `0.0.0.0` rather than loopback, so Mailpit's unauthenticated SMTP listener is reachable from the LAN/any network segment the host sits on, not just other processes on the same dev machine (the stated intent per the compose file's own comment).
- **Impact**: Low in the intended dev-only context (mail never leaves the box), but broader network exposure than the stated purpose requires, and a risky pattern if this compose file is ever reused with a real mail relay.
- **Remediation**: Bind to loopback only — `127.0.0.1:1025:1025`.

---

## 🟢 Low & 🔵 Informational Findings

### 🟢 [LOW-001] `guildId` extracted from untyped request bodies without validation
- **Severity**: 🟢 LOW
- **OWASP**: A06:2025 (Insecure Design) | **CWE**: CWE-20 (Improper Input Validation) | **NIST CSF**: GV.RM, PR.DS
- **Compliance**: ASVS V5 | ISO 27001 A.8.26
- **Location**: `backend/src/main/java/sk/gkanocz/aisauth/semester/SemesterController.java:111-113`, `backend/src/main/java/sk/gkanocz/aisauth/discordbot/CommandManagementController.java:143-146`
- `String guildId = (String) body.get("guildId")` cast from an untyped `Map<String, Object>` with no null/format check, unlike `GuildAccessAdminController.setAllowedGuilds`, which validates `\d{17,20}`. Currently fails closed (`hasLiveManagerRole` returns `false` for null/malformed IDs) so not exploitable today, but risks an unhandled 500 on malformed input and is inconsistent with the rest of the codebase.
- **Remediation**: Use typed request DTOs (as most other endpoints already do) instead of raw `Map<String,Object>` bodies, and validate guild-ID format centrally.

### 🟢 [LOW-002] `nginx:alpine` base image not version-pinned
- **Severity**: 🟢 LOW
- **OWASP**: A03:2025 | **CWE**: CWE-1104 | **NIST CSF**: GV.SC
- **Compliance**: ASVS V14.2 | PCI DSS 6.3 | SOC 2 CC8.1 | ISO 27001 A.8.8
- **Location**: `frontend/Dockerfile.frontend:17`
- `FROM nginx:alpine` floats to whatever the latest nginx release is on every rebuild, unlike `node:22-alpine` in the same file (major-version pinned) or `mailpit:v1.29.7` in `docker-compose.yml`.
- **Remediation**: Pin to a specific tag (e.g. `nginx:1.27-alpine`), consider digest-pinning both build stages.

### 🟢 [LOW-003] VPN sidecar container capabilities (`NET_ADMIN` + `/dev/net/tun`)
- **Severity**: 🟢 LOW (reviewed, assessed as necessary)
- **OWASP**: A02:2025 | **CWE**: CWE-269 | **NIST CSF**: PR.PS
- **Compliance**: ASVS V14.4 | ISO 27001 A.8.9
- **Location**: `infra/docker-compose.yml:34-37`
- This is the minimum capability set required for OpenVPN client mode (no `privileged: true`, no host networking) — genuinely necessary, not over-permissioned. Flagged for visibility only; see HOTSPOT-002.
- **Remediation**: No change required; add an inline comment documenting the justification for future maintainers.

### 🟢 [LOW-004] Unused `jwt-decode` dependency in frontend bundle
- **Severity**: 🟢 LOW
- **OWASP**: A03:2025 | **CWE**: CWE-1104 | **NIST CSF**: GV.SC
- **Compliance**: ISO 27001 A.8.29
- **Location**: `frontend/package.json:25`
- Listed as a production dependency with zero imports anywhere in `frontend/src` (verified by full-tree grep). Unnecessary attack surface and bundle weight; also a latent risk if wired in later for client-side JWT parsing (the access token is httpOnly today, so this would be inert, but the pattern invites misuse).
- **Remediation**: Remove from `package.json`/`package-lock.json`, or use deliberately with a comment.

### 🟢 [LOW-005] Backend API port published directly, bypassing the nginx proxy layer
- **Severity**: 🟢 LOW
- **OWASP**: A02:2025 | **CWE**: CWE-16 | **NIST CSF**: PR.PS
- **Compliance**: ASVS V14.1 | ISO 27001 A.8.9
- **Location**: `infra/docker-compose.yml:95-96`
- `backend` publishes `8080:8080` on top of nginx's `/api/` proxy, letting clients bypass nginx (and any header hardening it adds) and hit the backend directly.
- **Remediation**: Drop the host port publish for `backend` in the production compose file; keep it only in a separate dev-override file if direct debugging access is needed.

### 🔵 [INFO-001] LDAP tunnel is plaintext (`ldap://`) between backend, VPN sidecar and the university server
- **Severity**: 🔵 INFO
- **OWASP**: A04:2025 | **CWE**: CWE-319 | **NIST CSF**: PR.DS
- **Location**: `infra/docker-compose.yml:92`, `infra/vpn/entrypoint.sh:31`
- Both the `backend → vpn` hop and the `vpn → ldap.stuba.sk` hop use unencrypted `ldap://` (no LDAPS/StartTLS). The outer hop is protected by the OpenVPN tunnel and the inner hop is confined to the docker-compose bridge network, so exposure is limited to those trust boundaries. Worth confirming with whoever owns the LDAP client config whether the university server supports StartTLS.

### 🔵 [INFO-002] `style-src 'unsafe-inline'` in CSP
- **Severity**: 🔵 INFO
- **OWASP**: A05:2025 | **CWE**: CWE-79 (adjacent) | **NIST CSF**: PR.DS
- **Location**: `frontend/nginx.conf:17`
- Not broken — `script-src 'self'` has no `unsafe-inline`/`unsafe-eval`, which is what matters most for XSS mitigation. `style-src 'unsafe-inline'` is a common, accepted tradeoff for React apps using inline `style={{...}}`. No action required.

### 🔵 [INFO-003] Docker base images in backend not digest-pinned
- **Severity**: 🔵 INFO
- **OWASP**: A03:2025 | **CWE**: CWE-829 | **NIST CSF**: GV.SC
- **Compliance**: ISO 27001 A.8.8 | SOC 2 CC8.1
- **Location**: `backend/Dockerfile:2,12` (`eclipse-temurin:21-jdk`, `eclipse-temurin:21-jre-alpine`)
- Tags are reasonably specific (major version + variant) but not digest-pinned; a tag re-push upstream changes the build with no corresponding code change. Container already correctly runs as non-root (`appuser`, uid 1001).

### 🔵 [INFO-004] CI pipeline has no dependency/SCA scanning step
- **Severity**: 🔵 INFO
- **OWASP**: A03:2025 | **CWE**: CWE-1104 | **NIST CSF**: GV.SC, ID.RA
- **Compliance**: PCI DSS 11.3 | ISO 27001 A.8.8, A.8.29
- **Location**: `.github/workflows/backend-ci.yml`
- Only `./mvnw -B verify` runs — no `dependency-check`/Trivy/Grype/SBOM step visible in this workflow file (Dependabot/CodeQL may be configured at the repo-settings level outside this file; not verifiable from source).

---

## 🔲 Gray-Box Findings

### [GRAY-001] `/find` and `/user` Discord commands — role/permission enforcement
- **Severity**: 🟡 MEDIUM (same as MEDIUM-001 above)
- **OWASP**: A01:2025 | **CWE**: CWE-862 | **NIST CSF**: PR.AA
- **Tested As**: Ordinary Discord guild member (no manager/admin role)
- **Endpoint**: Discord slash commands `/find ais_id:<value>` and `/user discord:<value>`, dispatched via `CommandInteractionListener`
- **Expected**: Only guild managers/admins should be able to resolve a member's real email address
- **Actual**: Any guild member can invoke both commands and receive the target's email address, because neither command sets a `DefaultMemberPermissions` floor (unlike `/manualverify`/`/warn`, which are `ADMINISTRATOR`-only by default) and the per-guild `CommandPermissions` override defaults to empty/unrestricted
- **Request**: `/find ais_id:12345` typed by a non-privileged member in any onboarded guild's Discord channel
- **Remediation**: See MEDIUM-001.

### [GRAY-002] Super-admin authorization — session/claim freshness
- **Severity**: 🟡 MEDIUM (same as MEDIUM-002 above)
- **OWASP**: A07:2025 | **CWE**: CWE-613 | **NIST CSF**: PR.AA
- **Tested As**: Recently de-provisioned super admin holding an unexpired access token
- **Endpoint**: Any super-admin-gated route, e.g. `POST /api/wipe`, `PUT /api/admin/allowed-guilds`
- **Expected**: Removing an ID from `SUPER_ADMIN_IDS` and redeploying should immediately revoke super-admin access, matching how manager-role revocation is re-checked live on every request
- **Actual**: `GuildAccessService.isSuperAdmin()` reads only the JWT's `superAdmin` claim, which is set once at token issuance/refresh; a currently-valid access token keeps working for its full remaining TTL (default 300s) after the operator-side revocation
- **Request**: `POST /api/wipe` with a previously-issued, still-unexpired access-token cookie belonging to the removed super admin
- **Status**: ✅ Fixed — `isSuperAdmin()` now also checks `AdminProperties.superAdminIds()` live on every call.

---

## 📍 Security Hotspots

### [HOTSPOT-001] Authorization is 100% manual/per-controller with no declarative floor
- **OWASP**: A06:2025, secondary A01:2025 | **CWE**: CWE-862 (latent), CWE-284 | **NIST CSF**: ID.RA, GV.RM, PR.AA
- **Compliance**: ASVS V4 | SOC 2 CC6.1 | ISO 27001 A.8.3
- **Location**: `backend/src/main/java/sk/gkanocz/aisauth/config/SecurityConfig.java:49-59`; all 22 `@RestController` classes under `sk.gkanocz.aisauth`
- **Why sensitive**: No `@EnableMethodSecurity`, `@PreAuthorize`, `@Secured` or `@RolesAllowed` exists anywhere; `SecurityConfig` only enforces `.anyRequest().authenticated()`. Every guild-scoped or admin authorization check is a manual, copy-pasted call to `guildAccessService.assertCanManageGuild(...)`/`assertSuperAdmin(...)` inside each controller method. All 22 controllers were verified to call this correctly today, with `findByIdAndGuildId`-style repository lookups preventing cross-guild IDOR.
- **Risk if modified**: A future endpoint that forgets the one-line manual check is immediately open to any authenticated user, with nothing at compile time or route-config time to catch it.
- **Review guidance**: Any new controller method should be checked in review for a leading `guildAccessService` call; consider adding `@PreAuthorize` or a request-matcher-based role floor in `SecurityConfig` as a default-deny safety net on top of the existing manual checks.

### [HOTSPOT-002] VPN sidecar entrypoint/healthcheck — sole path to real LDAP directory
- **OWASP**: A08:2025, A02:2025 | **CWE**: CWE-269 | **NIST CSF**: GV.SC, PR.PS
- **Location**: `infra/vpn/entrypoint.sh`, `infra/vpn/healthcheck.sh`, `infra/docker-compose.yml:28-51`
- **Why sensitive**: This container holds `NET_ADMIN` + raw `/dev/net/tun` access and the real university VPN credential (`auth.txt`). `entrypoint.sh` currently fails closed correctly (`set -eu`, validates the auth-secret file is readable and has ≥2 lines before starting OpenVPN, `trap cleanup` kills both child processes together).
- **Risk if modified**: A change that weakens those guards, or a healthcheck change that can pass without a genuinely live authenticated tunnel, would let `backend`'s `depends_on: vpn: condition: service_healthy` gate pass while LDAP queries silently fail or reach a stale endpoint.
- **Review guidance**: Any change to `entrypoint.sh`'s validation checks or `healthcheck.sh`'s `ldapsearch` base-DN check should be reviewed specifically for fail-open regressions.

### [HOTSPOT-003] `frontend/src/lib/api.ts` — global 401/403 interceptor and redirect logic
- **OWASP**: A07:2025 | **CWE**: CWE-287 | **NIST CSF**: PR.AA
- **Location**: `frontend/src/lib/api.ts:8-30`
- **Why sensitive**: Single choke point deciding whether a 401 triggers a silent refresh-and-retry vs. a hard redirect to `/login`, and whether a 403 with the exact message string `'Manager access required'` triggers a forced logout. The string match brittly couples frontend behavior to exact backend wording.
- **Risk if modified**: A backend error-message wording change silently breaks the access-revoked UX; a change to the `_retry` guard could reintroduce an infinite refresh loop.
- **Review guidance**: Any PR touching backend 403 message text, or this interceptor, should be checked against the string match and the `_retry` flag logic.

### [HOTSPOT-004] `frontend/src/contexts/AuthContext.tsx` — OAuth code exchange and session bootstrap
- **OWASP**: A07:2025 | **CWE**: CWE-287 | **NIST CSF**: PR.AA
- **Location**: `frontend/src/contexts/AuthContext.tsx:31-68`
- **Why sensitive**: Trust boundary where an unauthenticated URL `?code=` param is exchanged for a session via `POST /auth/exchange`. Currently strips `code` from the URL immediately via `window.history.replaceState` (avoids leaking it through referrer/history) and only trusts `data.user` on a `response.ok` gate.
- **Risk if modified**: Skipping the URL-strip or the `response.ok` gating on a future refactor would reopen a code-leakage or spoofed-login-state window.
- **Review guidance**: Treat any change to this file as auth-critical; verify both behaviors survive refactors.

---

## 🧹 Code Smells

### [SMELL-001] `guildId` handling duplicated across controllers instead of a shared validated type (see LOW-001)
- **OWASP**: A06:2025 | **CWE**: CWE-20 | **NIST CSF**: GV.RM
- **Location**: `SemesterController.java`, `CommandManagementController.java`
- **Pattern**: Raw `Map<String,Object>` request bodies with manual, inconsistent casting instead of typed DTOs used elsewhere in the codebase.
- **Suggestion**: Introduce typed request DTOs with `@Valid`/format-validated `guildId` fields.

### [SMELL-002] Unused dependency shipped in the frontend bundle
- **OWASP**: A03:2025 | **CWE**: CWE-1104 | **NIST CSF**: GV.SC
- **Location**: `frontend/package.json:25` (`jwt-decode`)
- **Pattern**: Declared, never imported anywhere in `frontend/src`.
- **Suggestion**: Remove, or use deliberately with a comment justifying client-side decode (display-only, never an access decision).

### [SMELL-003] Repeated per-guild `localStorage` key construction without a shared helper
- **OWASP**: A06:2025 | **CWE**: N/A (maintainability) | **NIST CSF**: GV.RM
- **Location**: `frontend/src/pages/SwitchSemester.tsx:137,141,146-147`, `frontend/src/pages/Wipe.tsx:55,87,360`, `frontend/src/components/modules/shared.tsx:12-27`
- **Pattern**: Three files independently build `localStorage` keys via template strings instead of extending the existing `useSelectedGuildId` pattern in `shared.tsx`.
- **Security implication**: Low today (no sensitive data involved — just dismissed-console UI state), but no central place to enforce namespacing/expiry if this pattern is extended to something more sensitive later.
- **Suggestion**: Factor a shared `useGuildScopedLocalStorage(prefix, guildId)` helper.

---

## Recommendations Summary

**A01/A07 — Access control & auth freshness (highest priority)**
1. ✅ Fixed — `/find` no longer replies with email (MEDIUM-001 / GRAY-001).
2. ✅ Fixed — `superAdmin` claim re-validated live on every privileged request (MEDIUM-002 / GRAY-002).
3. Open — Add `@EnableMethodSecurity`/`@PreAuthorize` as a default-deny safety net over the existing manual per-controller checks (HOTSPOT-001).

**A02 — Infrastructure hardening**
4. ✅ Resolved — OpenVPN `tls-auth` key verified as STU's own public config, not a secret; no rotation needed (MEDIUM-003, downgraded to Info).
5. ✅ Fixed — `DB_PASSWORD` covered by `ProductionSecretsValidator`, Postgres port bound to loopback (MEDIUM-004).
6. ✅ Fixed — Mailpit SMTP bound to loopback (MEDIUM-005).

**A03 — Supply chain hygiene**
7. ✅ Fixed (nginx) / Open (backend digest pin) — `nginx:alpine` pinned to `nginx:1.27-alpine`; backend base image digest-pinning still needs a live registry lookup (LOW-002, INFO-003).
8. ✅ Fixed — Unused `jwt-decode` dependency removed (LOW-004 / SMELL-002).
9. Open — Add an SCA/dependency-vulnerability scan step to CI (INFO-004).

**A06 — Design hardening**
10. ✅ Fixed — `guildId`-bearing endpoints now validate via `GuildAccessService.requireValidGuildId()` (LOW-001 / SMELL-001).

Remaining open items: `@PreAuthorize` default-deny safety net (HOTSPOT-001) and CI dependency scanning (INFO-004) — neither urgent, both good next-pass candidates.

---

## Methodology

| Aspect | Details |
|--------|---------|
| Phases executed | 1–5 (full) |
| Frameworks detected | Spring Boot 4.1.0 (Spring Security, OAuth2 client + resource server, Spring Data JPA + Flyway/PostgreSQL, Spring Data LDAP, jjwt), React + TypeScript + Vite frontend (shadcn/ui), JDA 5.6.1 (Discord bot), nginx (SPA + reverse proxy), Docker Compose, OpenVPN sidecar |
| White-box categories | All 20 categories checked; findings in Broken Access Control, Security Misconfiguration, Supply Chain, Cryptographic Failures, Insecure Design, Authentication Failures, Data Integrity |
| Gray-box testing | Roles tested: ordinary Discord guild member, de-provisioned super admin. Endpoints probed: Discord slash commands, `/api/wipe`, `/api/admin/allowed-guilds`, all 22 REST controllers' guild-scoping checks |
| Security hotspots | 4 flagged: manual authorization architecture, VPN sidecar, frontend auth interceptor, OAuth code-exchange bootstrap |
| Code smells | 3 flagged: untyped request bodies, unused dependency, duplicated localStorage key pattern |
| Packs loaded | none |
| Scope exclusions | no `.security-audit-ignore` present |
| Baseline comparison | no `.security-audit-baseline.json` present |
| OWASP Top 10:2025 | 7/10 categories with findings (A01, A02, A03, A04, A06, A07, A08); A05, A09, A10 clean |
| NIST CSF 2.0 | GV, ID, PR covered by findings; DE, RS, RC clean |
| CWE | 15 unique CWE IDs identified |
| SANS/CWE Top 25 | 2/25 matched (#9, #17) |
| ASVS 5.0 | 7 chapters checked with findings (V2–V6, V8, V14) |
| Additional frameworks | PCI DSS 4.0.1, MITRE ATT&CK, SOC 2, ISO 27001:2022 |

**Areas explicitly verified clean**: LDAP query construction (parameterized `LdapQueryBuilder`, no injection), JPQL/native queries (single parameterized `@Query`, rest are Spring Data derived methods), JWT signing/decoding (HS256 pinned both ends, DB-backed session revocation via `AdminSession`), OAuth2 `state` CSRF protection (signed cookie repository, prior unauthenticated-deserialization issue already fixed), CORS (single explicit origin, not wildcarded), exception handling (generic `ProblemDetail` messages, no stack traces), Actuator exposure (only `health`/liveness/readiness probes exposed, no `/env`/`/beans`/`/heapdump`), SSRF surface (no `RestTemplate`/`WebClient`/`Runtime.exec` with user-controlled targets), frontend XSS surface (no `dangerouslySetInnerHTML`/`eval`, tokens are httpOnly cookies, no open redirects), npm audit (0 vulnerabilities), secrets-in-git (`.env` files correctly gitignored and untracked, `auth.txt` correctly excluded), CI workflow (no `pull_request_target` misuse, no untrusted-input interpolation, pinned first-party GitHub Actions).

---

*Report generated by Claude Security Audit*
