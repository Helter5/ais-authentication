# AIS Auth Platform

Prepis pôvodného Node.js/Discord bota ([ais-authentication](../ais-authentication)) do Java/Spring Boot + React/TypeScript stacku.
Cieľ projektu: naučiť sa Spring Boot a pokryť tech stack z pracovnej ponuky (Java, REST API, PostgreSQL, XML, TypeScript, React, GitHub, CI/CD, Maven, Docker, Keycloak, ...).

## Štruktúra repa

```
backend/    Spring Boot 4 (Java 21, Maven) — REST API, JPA, Security, LDAP, mail, Discord bot
infra/      docker-compose pre lokálne závislosti (PostgreSQL, Mailpit)
frontend/   React 19 + TypeScript + Vite admin dashboard (portnuté zo starého bota)
```

## Roadmapa

- [x] **M0** — Projekt skeleton (Maven, PostgreSQL, Flyway, CI)
- [x] **M1** — Doména (Student, VerificationCode), REST API, Spring LDAP, Spring Mail
- [x] **M2** — Spring Security + Discord OAuth2 + vlastný JWT login pre admin dashboard (zatiaľ super-admin-only, per-guild manager role príde s M3)
- [x] **M3** — Discord bot modul (JDA), `/verify` + `/code` napojené priamo na service vrstvu (rovnaký Spring kontext ako REST API — nie samostatný proces cez HTTP, presne ako v pôvodnom Node bote). Priraďovanie Discord roly po verifikácii príde s per-guild admin nastaveniami (M4).
- [ ] **M4** — Audit log, tickety, warns, scheduler, XML export/import (JAXB) — zatiaľ preskočené
- [x] **M5** — Frontend portnutý zo starého bota (Login, Discord OAuth, SelectServer, Users napojené na nový backend). Zvyšné stránky (Wipe, SwitchSemester, Modules, ReactionRoles, AutoDelete, HackedAccountTrap, Commands, Admin, Logs, DockerLogs, TicketTranscript) sú skopírované, ale nefunkčné, kým nepribudnú ich backend endpointy (M4+).
- [~] **M6 (čiastočne)** — Multi-stage Docker (backend aj frontend) + plný `docker-compose` (postgres, mailpit, backend, frontend za nginx reverse proxy). GitHub Actions/Jenkinsfile zatiaľ vynechané.
- [ ] **M7 (stretch)** — Keycloak namiesto vlastného JWT, WAR deploy na Tomcat/JBoss, AWS, Kotlin/Quarkus modul

## Lokálny vývoj

**Variant A — appky bežia natívne (rýchlejší reload pri vývoji):**

1. Nakopíruj `infra/.env.example` na `infra/.env` a uprav podľa potreby.
2. Spusti PostgreSQL + Mailpit: `docker compose -f infra/docker-compose.yml --env-file infra/.env up -d postgres mailpit`
3. Spusti backend: `cd backend && ./mvnw spring-boot:run` — beží na `http://localhost:8080`
4. Spusti frontend: `cd frontend && npm install && npm run dev` — beží na `http://localhost:5173`

**Variant B — celý stack v Dockeri:**

```
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d --build
```

Frontend (nginx, proxuje `/api/` na backend) beží na `http://localhost:8081`, backend priamo na `http://localhost:8080` (potrebné pre Discord OAuth redirect).

Pre reálne prihlásenie treba vlastnú Discord OAuth aplikáciu (`DISCORD_CLIENT_ID`/`DISCORD_CLIENT_SECRET` env vars) a Discord bota (`DISCORD_BOT_TOKEN`) — bez nich appka beží, ale login/bot sa nepripoja (očakávané, viď commit historyu M2/M3).
