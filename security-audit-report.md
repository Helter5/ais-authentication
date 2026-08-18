# Security Audit Report

**Project**: ais-auth-backend / spring-ais-authentication
**Date**: 2026-08-18
**Auditor**: Claude Security Audit
**Frameworks**: OWASP Top 10:2025 + NIST CSF 2.0
**Mode**: full

---

## Executive Summary

| Metric | Count |
|--------|-------|
| 🔴 Critical | 0 |
| 🟠 High | 0 |
| 🟡 Medium | 1 |
| 🟢 Low | 2 |
| 🔵 Informational | 2 |
| 🔲 Gray-box findings | 0 |
| 📍 Security hotspots | 3 |
| 🧹 Code smells | 1 |
| **Total findings** | **9** |

**Overall Risk Assessment**: Low. This repo went through a multi-pass `/security-audit` + `/security-review` series on 2026-08-18 (commits `cb4a656`…`5a7434e`) that already hardened the HTTP layer, fixed the X-Forwarded-For trust bug, added refresh-token reuse detection, closed a check-then-act race, and locked down `/info`/`/user`. This audit re-verified every one of those fixes is still in place (`ClientIpResolver`, `ProductionSecretsValidator`, digest-pinned images, `ADMIN_ONLY` on `/info`/`/user`, fail-closed CORS/CSRF reasoning) and found no regressions. The only genuinely new attack surface since that series is the `/pridatpredmet` self-service subject-role feature (14 commits, `b508ad0`…`db29c17`) and the new Caddy TLS-terminating edge — both reviewed in full below. Findings are minor: one supply-chain pinning gap and two exceptional-condition/fail-open-adjacent nits in the new feature, none exploitable for privilege escalation, data exposure, or auth bypass.

---

## OWASP Top 10:2025 Coverage

| OWASP ID | Category | Findings | Status |
|----------|----------|----------|--------|
| A01:2025 | Broken Access Control | 0 | ✅ Acceptable |
| A02:2025 | Security Misconfiguration | 0 | ✅ Acceptable |
| A03:2025 | Software Supply Chain Failures | 1 | 🔴 Needs Attention |
| A04:2025 | Cryptographic Failures | 0 | ✅ Acceptable |
| A05:2025 | Injection | 0 | ✅ Acceptable |
| A06:2025 | Insecure Design | 0 | ✅ Acceptable |
| A07:2025 | Authentication Failures | 0 | ✅ Acceptable |
| A08:2025 | Software or Data Integrity Failures | 0 | ✅ Acceptable |
| A09:2025 | Security Logging and Alerting Failures | 0 | ✅ Acceptable |
| A10:2025 | Mishandling of Exceptional Conditions | 2 | 🔴 Needs Attention |

---

## NIST CSF 2.0 Coverage

| Function | Categories | Findings | Status |
|----------|-----------|----------|--------|
| GV (Govern) | GV.OC, GV.RM, GV.RR, GV.PO, GV.OV, GV.SC | 1 | 🔴 Needs Attention |
| ID (Identify) | ID.AM, ID.RA, ID.IM | 0 | ✅ Acceptable |
| PR (Protect) | PR.AA, PR.AT, PR.DS, PR.PS, PR.IR | 0 | ✅ Acceptable |
| DE (Detect) | DE.CM, DE.AE | 2 | 🔴 Needs Attention |
| RS (Respond) | RS.MA, RS.AN, RS.CO, RS.MI | 0 | ✅ Acceptable |
| RC (Recover) | RC.RP, RC.CO | 0 | ✅ Acceptable |

---

## 🟡 Medium Findings

### 🟡 [MEDIUM-001] Caddy image is tag-pinned, not digest-pinned — breaks the repo's own supply-chain pattern
- **Severity**: 🟡 MEDIUM
- **OWASP**: A03:2025 (Software Supply Chain Failures)
- **CWE**: CWE-1104 (Use of Unmaintained Third-Party Components) / CWE-829 (Inclusion of Functionality from Untrusted Control Sphere)
- **NIST CSF**: GV.SC (Supply Chain Risk Management)
- **Compliance**: SANS Top 25 n/a | ASVS V14 | PCI DSS 6.3 | ATT&CK T1195 (Supply Chain Compromise) | SOC 2 CC8.1 | ISO 27001 A.8.29
- **Location**: `infra/docker-compose.yml:125`
- **Attack Vector**: The Caddy image is pulled as `caddy:2-alpine`, a floating tag. Every other image in this same compose file (`postgres:16-alpine@sha256:...`, `eclipse-temurin:...@sha256:...` in the backend Dockerfile) is deliberately digest-pinned, with an explicit code comment explaining why: "a force-moved tag or registry compromise can't silently change what ships." Caddy is the new internet-facing TLS-terminating edge (added this series, `9485b74`) — if the `2-alpine` tag is ever repointed (compromised upstream, registry MITM, or just an unreviewed breaking update), the next `docker compose pull && up` silently ships a different binary sitting directly on the public port.
- **Impact**: Loss of build reproducibility and supply-chain integrity guarantees for the component that terminates TLS for the whole application. Not currently exploitable by an external attacker without a registry-level compromise, but it's the one image in the stack that doesn't get the protection the team explicitly designed in for every other image.
- **Remediation**: Pin `caddy` to a digest the same way `postgres` and the JDK/JRE images are pinned, and re-pin deliberately on updates via `docker buildx imagetools inspect caddy:2-alpine`.

---

## 🟢 Low Findings

### 🟢 [LOW-001] `/pridatpredmet` approval marks a request APPROVED before confirming the Discord role grant succeeded
- **Severity**: 🟢 LOW
- **OWASP**: A10:2025 (Mishandling of Exceptional Conditions)
- **CWE**: CWE-755 (Improper Handling of Exceptional Conditions) / CWE-390 (Detection of Error Condition Without Action)
- **NIST CSF**: DE.AE (Adverse Event Analysis)
- **Compliance**: ASVS V7 (Error Handling) | SOC 2 CC7.4 | ISO 27001 A.8.16
- **Location**: `backend/src/main/java/sk/gkanocz/aisauth/discordbot/SubjectRoleButtonListener.java:104-109`
- **Attack Vector**: `guild.addRoleToMember(target, role).reason(...).queue()` is fire-and-forget with no failure callback. The very next lines unconditionally call `subjectRoleService.decide(request, APPROVED, ...)`, edit the embed to show "Approved", and DM the student "Tvoja žiadosť ... bola schválená". If the Discord API call fails (bot's role was moved below the target role between request and approval, rate limit, transient API error), the database and the user both record/see "approved" while the role was never actually granted — a silent success/failure divergence in a business-logic (not purely cosmetic) flow.
- **Impact**: Low — no security control is bypassed (the opposite: a student ends up *without* an entitlement the system believes they have), but it can mask a real failure with no operator-visible signal, and the same `.queue()`-without-error-handler pattern is used throughout the Discord bot code (`RoleMenuInteractionListener`, `SemesterOperationService`, the auto-grant path in `SubjectRoleSlashCommandListener.java:128`), so this is a systemic style, not unique to this file — flagged here because it's the newest instance and the one most directly tied to an admin-facing "approved/rejected" audit record.
- **Remediation**: Not code-fixed in this pass (`--fix` not requested). Add a `.queue(success -> ..., failure -> log.warn(...) )` failure consumer before writing `APPROVED`/DMing the user, or move the `decide()`/DM call into the success callback.

### 🟢 [LOW-002] `SubjectRoleService.semesterResetAt` silently falls back to a fixed epoch on any parse failure
- **Severity**: 🟢 LOW
- **OWASP**: A10:2025 (Mishandling of Exceptional Conditions)
- **CWE**: CWE-390 (Detection of Error Condition Without Action)
- **NIST CSF**: DE.AE
- **Compliance**: ASVS V7 | ISO 27001 A.8.16
- **Location**: `backend/src/main/java/sk/gkanocz/aisauth/subjectrole/SubjectRoleService.java:32-42`
- **Attack Vector**: `catch (Exception e) { return EPOCH; }` swallows every possible parse failure (corrupted `admin_settings` row, unexpected format) with zero logging. `EPOCH` is 2000-01-01, meaning a parse failure silently makes `activeCount()` count *every* granted/pending/approved request the user has ever made across all semesters, not just the current one — the opposite of fail-open (it's stricter, not a security bypass), but it's a silent failure that would be confusing to debug and has no log line to alert on if `admin_settings.subjectrole_reset_<guild>` ever gets corrupted.
- **Impact**: Low — fails toward the more restrictive behavior (blocks auto-grant sooner), not an exploitable bypass. Purely a diagnosability gap.
- **Remediation**: Add `log.warn("failed to parse subjectrole_reset_{}, falling back to epoch", guildId, e)` in the catch block, consistent with the `DashboardAuditLogger` fix from the prior audit series (commit `519d125`) which added logging to an identically-shaped silent catch.

---

## 🔵 Informational Findings

### 🔵 [INFO-001] `migrate-export.js` builds SQL via manual string concatenation
- **Severity**: 🔵 INFO
- **OWASP**: A05:2025 (Injection)
- **CWE**: CWE-89 (SQL Injection) — theoretical only, see below
- **NIST CSF**: PR.DS
- **Compliance**: ASVS V5.3 | PCI DSS 6.2.4 | SANS Top 25 #3
- **Location**: `migrate-export.js:38-41` (`sqlStr` helper) and every `log(\`INSERT INTO ...\`)` call
- **Attack Vector**: `sqlStr()` builds Postgres string literals by manually doubling single quotes (`'${String(v).replace(/'/g, "''")}'`). This is correct escaping for Postgres's default `standard_conforming_strings=on` (backslashes aren't special), so it is not currently exploitable. It's flagged only because the values passed through it include old-bot free-text fields an end user could have influenced indirectly (e.g. `warns.reason`, `autodelete_configs.notify_message`) — the script is explicitly a manual, human-reviewed, operator-run one-off (`node migrate-export.js ... > migration.sql`, "Then REVIEW migration.sql before applying it"), never a running service, so there's no remote attacker path here today.
- **Impact**: None under current usage. Would matter only if this script were ever adapted into an automated/unattended pipeline.
- **Remediation**: Not code-fixed (informational, no `--fix` requested, script is explicitly designed for manual review). If ever automated, switch to parameterized `pg` client inserts instead of string-built SQL.

### 🔵 [INFO-002] Caddy edge adds no security headers of its own
- **Severity**: 🔵 INFO
- **OWASP**: A02:2025 (Security Misconfiguration)
- **CWE**: CWE-16
- **NIST CSF**: PR.PS
- **Compliance**: ASVS V14
- **Location**: `infra/Caddyfile:1-7`
- **Attack Vector**: None — verified as a non-issue. `nginx.conf` (the origin Caddy proxies to) already sets `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy` and a strict `Content-Security-Policy` on every response, and Caddy passes those through unmodified as a plain `reverse_proxy`. Listed here only so the "headers configured" checklist item shows an explicit answer rather than an unchecked box for the newest layer in the stack — no action needed.
- **Impact**: None.
- **Remediation**: None required. Optional hardening only: enabling HSTS at Caddy (which does TLS termination, unlike nginx which is commented out pending "once a TLS-terminating layer sits in front") would be the natural place for it now that one exists.

---

## 📍 Security Hotspots

### [HOTSPOT-001] Discord role-grant/removal calls across the bot are fire-and-forget (`.queue()` without failure handling)
- **OWASP**: A10:2025 | A09:2025
- **CWE**: CWE-755
- **NIST CSF**: DE.AE
- **Compliance**: ASVS V7 | ISO 27001 A.8.16
- **Location**: `discordbot/SubjectRoleButtonListener.java`, `discordbot/SubjectRoleSlashCommandListener.java`, `discordbot/RoleMenuInteractionListener.java`, `semester/SemesterOperationService.java` — every `guild.addRoleToMember(...).queue()` / `removeRoleFromMember(...).queue()` call
- **Why sensitive**: This is where "the database/UI says X happened" and "Discord actually did X" can diverge silently. Currently benign (see LOW-001), but any future code that makes a *security* decision based on the recorded outcome (e.g. "role was granted → do the sensitive follow-up action") without checking the actual Discord API result would inherit a fail-open-shaped bug.
- **Risk if modified**: A PR that adds a security-relevant side effect after one of these calls (e.g. "grant role AND immediately also disable audit logging for this user" or any privilege-adjacent chained action) should not assume the `.queue()` call succeeded.
- **Review guidance**: When reviewing new code near these call sites, check whether the surrounding logic's correctness depends on the Discord call having actually succeeded, and if so, require a `.queue(success, failure)` pair or `.complete()` (as `WipeService` and `SemesterOperationService`'s synchronous paths already do in several other spots).

### [HOTSPOT-002] `SubjectRoleSettings` / `cmd_settings_*` blob is a schemaless `Map<String,Object>` shared by 3 listeners
- **OWASP**: A06:2025
- **CWE**: CWE-20 (Improper Input Validation)
- **NIST CSF**: GV.RM
- **Compliance**: ASVS V1, V5
- **Location**: `discordbot/SubjectRoleSettings.java`, `settings/AdminSettingsService` consumers
- **Why sensitive**: `allowedRoleIds` / `approverRoleIds` come out of a generic JSON blob (`cmd_settings_<guild>_pridatpredmet`) with only a `List<?> → filter String.class::isInstance` type check, no bound on list size and no re-validation that the IDs are real guild roles at read time (that check happens downstream, per-use, via `guild.getRoleById`/membership checks — which is correct and sufficient today). Flagged as a hotspot, not a finding, because the *security-critical* property (only admin-approved roles are grantable) currently depends on every future reader of this blob re-deriving the allowlist correctly and re-checking role existence, rather than on a validated/typed model.
- **Risk if modified**: A future code path that reads `allowedRoleIds` and grants a role without re-checking `allowedRoleIds.contains(role.getId())` at the point of the grant (mirroring what `SubjectRoleSlashCommandListener.process()` does today) would silently reopen self-service role grant to arbitrary guild roles.
- **Review guidance**: Any new consumer of `SubjectRoleSettings.allowedRoleIds()`/`approverRoleIds()` must re-check membership against the current allowlist immediately before acting, not trust a value cached or passed from an earlier check.

### [HOTSPOT-003] `ProductionSecretsValidator` covers exactly two placeholders — new required secrets won't get the same fail-fast guard automatically
- **OWASP**: A02:2025
- **CWE**: CWE-16, CWE-260
- **NIST CSF**: PR.PS
- **Compliance**: PCI DSS 2.2 | ISO 27001 A.8.9
- **Location**: `backend/src/main/java/sk/gkanocz/aisauth/config/ProductionSecretsValidator.java:28-29`
- **Why sensitive**: This is the repo's single most important prod fail-fast guard (per its own doc comment: a leaked default JWT secret lets anyone mint a forged `superAdmin: true` token). It hardcodes exactly two checks (`DEV_PLACEHOLDER_SECRET`, `DEV_PLACEHOLDER_DB_PASSWORD`). `DISCORD_CLIENT_SECRET`, `DISCORD_BOT_TOKEN`, and `SUPER_ADMIN_IDS` are all `:?...must be set` in `docker-compose.yml` (so Compose itself refuses to start without them) but have no equivalent same-class "is this still an obviously-fake dev value" check inside the app.
- **Risk if modified**: Not a current gap (Compose's `:?` already prevents blank/unset), but if any future required secret gets a *non-blank* insecure default added to `application.yml` (the way `JWT_SECRET` and `DB_PASSWORD` currently have), it won't be caught unless someone remembers to also add a check here.
- **Review guidance**: When adding a new `app.*` secret with a hardcoded fallback in `application.yml`, add the matching placeholder-equality check to `ProductionSecretsValidator`.

---

## 🧹 Code Smells

### [SMELL-001] `Commands.tsx`'s per-command settings schema/defaults are three parallel `Record<string, ...>` maps kept in sync by hand
- **OWASP**: A06:2025
- **CWE**: CWE-1104 (maintainability, not a CWE-typed security bug)
- **NIST CSF**: GV.RM
- **Compliance**: ISO 27001 A.8.25
- **Location**: `frontend/src/pages/Commands.tsx` — `CMD_SETTINGS_SCHEMA`, `CMD_SETTINGS_DEFAULTS`, `CMD_MESSAGE_FIELD`, `CMD_LOG_EVENT_TYPES`
- **Pattern**: Adding `/pridatpredmet` required touching 4 separate `Record<string, ...>` constants that all key off the same command name string, with no compiler-enforced link between them (a typo'd key in one map silently no-ops instead of erroring).
- **Security implication**: Not itself a vulnerability — every existing entry is currently consistent — but it's the kind of structure where a future command's settings toggle (e.g. an `allowedRoles`-shaped admin control gating something security-relevant) could be added to `CMD_SETTINGS_SCHEMA` and silently never rendered/saved because the matching `CMD_SETTINGS_DEFAULTS` entry was forgotten, leaving an admin believing a restriction is configured when the UI never persisted it.
- **Suggestion**: Not code-fixed (`--fix` not requested; this is a frontend admin-UI structural note, not a runtime risk). Consider a single `Record<string, CommandSchema>` where each command's schema, defaults, and message-field metadata live together as one object, so a missing piece is a type error instead of a silent no-op.

---

## Recommendations Summary

1. **A03:2025** — Digest-pin the `caddy:2-alpine` image in `infra/docker-compose.yml`, matching the pattern already used for `postgres` and the JDK/JRE base images. (MEDIUM-001)
2. **A10:2025** — Add failure callbacks to the two new `/pridatpredmet` Discord API calls that write an "approved"/"granted" outcome before confirming the underlying role grant succeeded. (LOW-001, HOTSPOT-001)
3. **A10:2025** — Log the swallowed parse exception in `SubjectRoleService.semesterResetAt`. (LOW-002)
4. Everything else is informational/hotspot/smell — no action required before shipping; use HOTSPOT-002/003 as review checklist items for future PRs in these areas.

No findings in Broken Access Control, Injection, Authentication, Cryptographic Failures, or Data Integrity — the HTTP-facing surface remains well-hardened, consistent with the prior audit series' conclusion that this repo's marginal risk lives in business-logic/Discord-bot code, not the REST API layer.

---

## Methodology

| Aspect | Details |
|--------|---------|
| Phases executed | 1-5 (full) |
| Frameworks detected | Spring Boot 4.1 (Java 21) backend; React 19 + Vite 8 + TypeScript frontend; JDA 5.6.1 Discord bot embedded in the same Spring app; Postgres 16 + Flyway; Caddy 2 (TLS edge) + nginx (SPA/API proxy) |
| White-box categories | All 20 categories from `attack-vectors.md`, weighted toward areas changed since the 2026-08-18 audit series (subject-role self-service feature, LDAP batch lookup, multi-guild command registration, Caddy edge) |
| Gray-box testing | Not applicable in `full` mode as executed — role/permission logic for the new `/pridatpredmet` approve/reject flow was verified by static review (guild-scoped `findByIdAndGuildId`, `ADMINISTRATOR` OR configured `approverRoleIds` check) rather than live probing, since this is a Discord-bot interaction surface, not an HTTP API |
| Security hotspots | 3 flagged (fire-and-forget Discord API pattern, schemaless command-settings blob, prod-secrets validator coverage) |
| Code smells | 1 flagged (frontend parallel-maps schema structure) |
| Packs loaded | none |
| Scope exclusions | none (`.security-audit-ignore` not present) |
| Baseline comparison | none (`.security-audit-baseline.json` not present) |
| OWASP Top 10:2025 | 10/10 categories covered |
| NIST CSF 2.0 | GV, ID, PR, DE, RS, RC all covered |
| CWE | 8 unique CWE IDs identified |
| SANS/CWE Top 25 | #3 (SQLi pattern, informational-only) referenced |
| ASVS 5.0 | V1, V5, V7, V14 referenced |
| Additional frameworks | PCI DSS 4.0.1, MITRE ATT&CK, SOC 2, ISO 27001:2022 all referenced per finding |

---

*Report generated by Claude Security Audit*
