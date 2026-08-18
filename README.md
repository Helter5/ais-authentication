# AIS Auth Platform

Discord bot + admin dashboard pre FEI STUBA server. Overuje študentov cez univerzitný LDAP ([AIS](https://is.stuba.sk/)), prideľuje im Discord rolu. Vytvorené pre študentov [FEI STUBA](https://www.fei.stuba.sk/).

Prepis staršieho Node.js bota ([ais-authentication](../ais-authentication)) na Java stack.

## Stack

- **Backend** — Spring Boot 4, Java 21, Maven. REST API, Spring Security (`oauth2-client`), Spring LDAP, JPA/PostgreSQL, Flyway.
- **Discord bot** — JDA, beží v tom istom procese ako backend (slash commandy volajú service vrstvu priamo, nie samostatný proces cez HTTP).
- **Frontend** — React 19, TypeScript, Vite.
- **Deployment** — `docker compose` (Postgres, Mailpit, backend, frontend za Caddy).

## Funkcie

- **Overenie študentov** — `/verify`, `/code`, `/manualverify`. Používateľ zadá AIS ID, dostane overovací kód na univerzitný e-mail (Spring Mail/Mailpit), po zadaní kódu appka overí identitu voči LDAP a prideľuje Discord rolu.
- **Admin dashboard** — per-guild správa cez Discord OAuth2 login. Nastavenia bota, warny (`/warn`, `/warns`, auto-punishment), audit log, access log, správa príkazov a rolí, hromadný re-check/wipe overených členov voči LDAP, prepínanie semestra, automatické mazanie správ, „Hacked Account Trap" automod (nastaviteľný trap kanál — ktokoľvek doňho napíše mimo výnimiek dostane permanent ban, voliteľne DM pred banom a zmazanie správ za posledných X hodín, všetko zalogované do audit logu aj spam logu).
- **Autorizácia** — super-admini (celá appka) + per-guild manažéri (len svoj server), vynucované na každom endpointe cez `GuildAccessService`.
- **Prihlasovanie** — Discord OAuth2 (`spring-boot-starter-oauth2-client`, vlastná `ClientRegistration` pre Discord). Appka si sama vydáva a validuje JWT session token + refresh token, žiadny externý identity provider.

## Štruktúra repa

```
backend/    Spring Boot 4 (Java 21, Maven) — REST API, JPA, Security, LDAP, mail, Discord bot (JDA)
infra/      docker-compose (PostgreSQL, Mailpit, backend, frontend/Caddy)
frontend/   React 19 + TypeScript + Vite admin dashboard
```

## Lokálny vývoj

**Variant A — appky natívne (rýchlejší reload):**

```
cp infra/.env.example infra/.env   # uprav podľa potreby
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d postgres mailpit
cd backend && ./mvnw spring-boot:run     # http://localhost:8080
cd frontend && npm install && npm run dev  # http://localhost:5173
```

**Variant B — celý stack v Dockeri:**

```
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
```

Frontend (za Caddy, proxuje `/api/` na backend) beží na `http://localhost:8081`, backend priamo na `http://localhost:8080` (potrebné pre Discord OAuth redirect).

Pre reálne prihlásenie treba vlastnú Discord OAuth aplikáciu (`DISCORD_CLIENT_ID`/`DISCORD_CLIENT_SECRET`) a Discord bota (`DISCORD_BOT_TOKEN`) — bez nich appka beží, ale login/bot sa nepripoja.

### Discord Developer Portal setup

**OAuth2 → Redirects**
Presné URI podľa `DISCORD_REDIRECT_URI` (`application.yml`), napr. `http://localhost:8080/api/auth/discord/callback` pre dev, + produkčná URL ak beží inde. Musí sedieť exactne (aj scheme/port).

**Bot → Privileged Gateway Intents**
`SERVER MEMBERS INTENT` + `MESSAGE CONTENT INTENT` — obe zapnúť (kód ich enable-uje v `DiscordBotService.java`). `PRESENCE INTENT` netreba.

**OAuth2 → General**
`Client ID`/`Client Secret` musia sedieť s `DISCORD_CLIENT_ID`/`DISCORD_CLIENT_SECRET` v env.

**Bot → Token**
`DISCORD_BOT_TOKEN` v env — reset tokenu v konzole = treba updatnúť env, inak bot sa nepripojí.

**Bot invite** (samostatný krok od login OAuth, cez OAuth2 URL Generator)
- scopes: `bot` + `applications.commands` (bez druhého slash commandy nepôjdu)
- permissions: Ban Members, Kick Members, Manage Channels, Manage Roles, Moderate Members (timeout), Send Messages, View Channels

**Role hierarchy** (nie checkbox v konzole, ale často zabudnuté)
Bot rola musí byť v server settings vyššie než rola, ktorú prideľuje/spravuje — inak `MANAGE_ROLES` zlyhá na Discord API úrovni.

**Public Bot** (Bot tab) — netreba zapínať, ak invite link zdieľaš len ty/tím.

## Konfigurácia

Všetko cez env vars, defaulty v `backend/src/main/resources/application.yml`.

**Overenie**
- `allowed-faculties` (`app.verification.allowed-faculties`, zoznam v `application.yml`, netreba env) — zoznam LDAP org-unit skratiek, ktoré appka pustí, teraz len `fei-stud`.
- `required-account-status` (`application.yml`, netreba env) — AIS status potrebný na overenie, teraz `student:active`.
- `VERIFICATION_TESTING_MODE` (default `false`) — `true` = `/verify` preskočí mail, kód ukáže priamo v Discorde.

**LDAP** — `LDAP_URL` (default `ldap://localhost:389`), `LDAP_BASE` (default `ou=People,dc=stuba,dc=sk`), `LDAP_USERNAME`, `LDAP_PASSWORD`.

**Mail** — `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS`, `MAIL_SMTP_SSL`.

**Discord** — `DISCORD_CLIENT_ID`, `DISCORD_CLIENT_SECRET`, `DISCORD_REDIRECT_URI`, `DISCORD_BOT_TOKEN`, `DISCORD_GUILD_ID` (ak nastavené, slash commandy sa registrujú len na tento server, okamžite).

**Admin** — `SUPER_ADMIN_IDS` (Discord ID zoznam, čiarkou oddelené) — super-admini appky, obídu per-guild `GuildAccessService` kontrolu.

**JWT** — `JWT_SECRET`, `JWT_ACCESS_TOKEN_TTL_SECONDS` (default 300), `JWT_REFRESH_TOKEN_TTL_SECONDS` (default 2592000).

**Ostatné** — `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD`, `SERVER_PORT` (default 8080), `FRONTEND_URL` (default `http://localhost:5173`).

## Testy

Backend: 86 test súborov (JUnit 5, Mockito, Spring Boot Test, Testcontainers-Postgres pre integračné testy — reálna DB, nie mock). Pokrývajú service vrstvu, controllery (`*ControllerIntegrationTest`, plný Spring context + skutočný Postgres), Discord bot listenery/commandy, automod (Hacked Account Trap, auto-delete, auto-mention), warn systém, wipe/re-check proti LDAP, audit log, autorizáciu (`GuildAccessService`), role menu, semester switching, scheduling. Nepokryté zámerne: entity, DTO, exceptions, repository interfaces, config triedy (žiadna vlastná logika).

Spusti: `cd backend && ./mvnw test` (rýchle, bez integračných) alebo `./mvnw verify` (aj integračné, spúšťa Testcontainers → treba bežiaci Docker).

## CI

GitHub Actions (`backend-ci.yml`) spúšťa `./mvnw verify` proti reálnemu Postgresu cez Testcontainers pri každom push/PR dotýkajúcom sa `backend/`.

## Stav

Jadro appky (overenie, dashboard, autorizácia, audit log, Discord OAuth2 login s vlastným JWT, Docker nasadenie, CI) hotové a funkčné. Otvorené: WAR deploy na Tomcat/JBoss, nasadenie na AWS, samostatný modul v Kotline/Quarkuse.

## Screenshoty

<table>
<tr>
<td width="50%"><img src="docs/screenshots/discord-verify.png" width="100%"><br><sub><b>Discord — <code>/verify</code> flow</b><br>AIS ID → overovací kód na e-mail → pridelenie roly.</sub></td>
<td width="50%"><img src="docs/screenshots/login.png" width="100%"><br><sub><b>Login</b><br>Discord OAuth2 login.</sub></td>
</tr>
<tr>
<td><img src="docs/screenshots/select-server.png" width="100%"><br><sub><b>Select server</b><br>Výber servera — super-admin vidí všetky, manažér len svoje.</sub></td>
<td><img src="docs/screenshots/dashboard.png" width="100%"><br><sub><b>Dashboard</b><br>Stav bota, počet overených členov, aktívne kódy.</sub></td>
</tr>
<tr>
<td><img src="docs/screenshots/users.png" width="100%"><br><sub><b>Users</b><br>Overení členovia, hromadný re-check/wipe proti LDAP.</sub></td>
<td><img src="docs/screenshots/codes.png" width="100%"><br><sub><b>Codes</b><br>Aktívne overovacie kódy.</sub></td>
</tr>
<tr>
<td><img src="docs/screenshots/warns.png" width="100%"><br><sub><b>Warns</b><br><code>/warn</code>, <code>/warns</code>, auto-punishment.</sub></td>
<td><img src="docs/screenshots/modules.png" width="100%"><br><sub><b>Modules</b><br>Prehľad zapnutých modulov.</sub></td>
</tr>
<tr>
<td><img src="docs/screenshots/hacked-account-trap.png" width="100%"><br><sub><b>Hacked Account Trap</b><br>Trap kanál, výnimky, akcia pri triggeri.</sub></td>
<td><img src="docs/screenshots/auto-delete.png" width="100%"><br><sub><b>Auto delete</b><br>Automatické mazanie správ.</sub></td>
</tr>
<tr>
<td><img src="docs/screenshots/auto-mentions.png" width="100%"><br><sub><b>Auto mentions</b><br>Automod pre mention spam.</sub></td>
<td><img src="docs/screenshots/role-menu.png" width="100%"><br><sub><b>Role menu</b><br>Self-service roly cez Discord tlačidlá.</sub></td>
</tr>
<tr>
<td><img src="docs/screenshots/commands.png" width="100%"><br><sub><b>Commands</b><br>Zapínanie/vypínanie príkazov, permission overrides.</sub></td>
<td><img src="docs/screenshots/logs.png" width="100%"><br><sub><b>Logs</b><br>Audit log a access log admin/moderátorských akcií.</sub></td>
</tr>
<tr>
<td><img src="docs/screenshots/settings.png" width="100%"><br><sub><b>Settings</b><br>Nickname bota, timezone, per-guild nastavenia.</sub></td>
<td><img src="docs/screenshots/switch-semester.png" width="100%"><br><sub><b>Switch semester</b><br>Prepínanie semestra.</sub></td>
</tr>
<tr>
<td><img src="docs/screenshots/admin.png" width="100%"><br><sub><b>Admin</b><br>Globálna super-admin sekcia (mimo per-guild).</sub></td>
<td></td>
</tr>
</table>
