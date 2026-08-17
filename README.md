# AIS Auth Platform

**TL;DR:** Discord bot + admin dashboard pre FEI STUBA server, ktorý overuje študentov cez univerzitný LDAP (AIS) a prideľuje im Discord rolu. Backend v Spring Boot 4 / Java 21 (REST API, Spring Security + `spring-boot-starter-oauth2-client`, Spring LDAP, JPA/PostgreSQL, Flyway), Discord bot bežiaci priamo v tom istom procese cez JDA, prihlásenie cez Discord OAuth2 s vlastným JWT session tokenom, frontend v React 19 + TypeScript. Celé nasadenie beží ako `docker compose` (Postgres, Mailpit, backend, frontend za nginx).

Vzniklo ako prepis staršieho Node.js bota ([ais-authentication](../ais-authentication)) na Java stack.

## Čo appka robí

- **Overenie študentov** — používateľ zadá svoje AIS ID, appka mu pošle overovací kód na univerzitný e-mail (cez Spring Mail/Mailpit), po zadaní kódu overí identitu voči LDAP a prideľuje Discord rolu (`/verify`, `/code`, príp. manuálne cez `/manualverify`).
- **Admin dashboard** (React SPA) — per-server (per-guild) správa cez Discord OAuth2 login: prehľad servera, nastavenia bota (nickname, timezone), warny (`/warn`, `/warns`, auto-punishment pri prekročení limitu), audit log a access log každej admin/moderátorskej akcie, správa príkazov a rolí, hromadný re-check/„wipe" overených členov voči LDAP, prepínanie semestra, automatické mazanie správ, „Hacked Account Trap" automod modul, ticket transcripty.
- **Autorizácia** — super-admini (celá appka) a per-guild manažéri (len svoj server), vynucované na každom endpointe cez `GuildAccessService`.
- **Prihlasovanie** — Discord OAuth2 login cez Spring Security `oauth2Login` (`spring-boot-starter-oauth2-client`, vlastná `ClientRegistration` pre Discord), appka si sama vydáva a validuje session token (JWT) aj refresh token, žiadny externý identity provider.

## Štruktúra repa

```
backend/    Spring Boot 4 (Java 21, Maven) — REST API, JPA, Security, LDAP, mail, Discord bot (JDA)
infra/      docker-compose (PostgreSQL, Mailpit, backend, frontend/nginx)
frontend/   React 19 + TypeScript + Vite admin dashboard
```

Bot beží v rovnakom Spring kontexte ako REST API (nie samostatný proces cez HTTP) — slash commandy volajú service vrstvu priamo.

## Lokálny vývoj

**Variant A — appky bežia natívne (rýchlejší reload pri vývoji):**

1. Nakopíruj `infra/.env.example` na `infra/.env` a uprav podľa potreby.
2. Spusti závislosti: `docker compose -f infra/docker-compose.yml --env-file infra/.env up -d postgres mailpit`
3. Spusti backend: `cd backend && ./mvnw spring-boot:run` — beží na `http://localhost:8080`
4. Spusti frontend: `cd frontend && npm install && npm run dev` — beží na `http://localhost:5173`

**Variant B — celý stack v Dockeri:**

```
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
```

Frontend (nginx, proxuje `/api/` na backend) beží na `http://localhost:8081`, backend priamo na `http://localhost:8080` (potrebné pre Discord OAuth redirect).

Pre reálne prihlásenie treba vlastnú Discord OAuth aplikáciu (`DISCORD_CLIENT_ID`/`DISCORD_CLIENT_SECRET`) a Discord bota (`DISCORD_BOT_TOKEN`) — bez nich appka beží, ale login/bot sa nepripoja.

## CI

GitHub Actions (`backend-ci.yml`) spúšťa `./mvnw verify` proti reálnemu Postgresu cez Testcontainers pri každom push/PR dotýkajúcom sa `backend/`.

## Stav / plánované rozšírenia

Jadro appky (overenie, dashboard, autorizácia, audit log, Discord OAuth2 login s vlastným JWT, Docker nasadenie, CI) je hotové a funkčné. Otvorené zostávajú len voliteľné rozšírenia: WAR deploy na Tomcat/JBoss, nasadenie na AWS, samostatný modul v Kotline/Quarkuse.
