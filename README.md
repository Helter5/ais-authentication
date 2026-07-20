# AIS Auth Platform

Prepis pôvodného Node.js/Discord bota ([ais-authentication](../ais-authentication)) do Java/Spring Boot + React/TypeScript stacku.
Cieľ projektu: naučiť sa Spring Boot a pokryť tech stack z pracovnej ponuky (Java, REST API, PostgreSQL, XML, TypeScript, React, GitHub, CI/CD, Maven, Docker, Keycloak, ...).

## Štruktúra repa

```
backend/    Spring Boot 4 (Java 21, Maven) — REST API, JPA, Security, LDAP, mail
infra/      docker-compose pre lokálne závislosti (PostgreSQL, ...)
frontend/   React + TypeScript dashboard (pribudne v milestone 5)
```

## Roadmapa

- [x] **M0** — Projekt skeleton (Maven, PostgreSQL, Flyway, CI)
- [ ] **M1** — Doména (Student, VerificationCode), REST API, Spring LDAP, Spring Mail
- [ ] **M2** — Spring Security + vlastný JWT login pre admin dashboard
- [ ] **M3** — Discord bot modul (JDA) napojený na REST vrstvu
- [ ] **M4** — Audit log, tickety, warns, scheduler, XML export/import (JAXB)
- [ ] **M5** — Frontend napojený na nové API
- [ ] **M6** — Docker (multi-stage build), plný docker-compose, GitHub Actions, Jenkinsfile
- [ ] **M7 (stretch)** — Keycloak namiesto vlastného JWT, WAR deploy na Tomcat/JBoss, AWS, Kotlin/Quarkus modul

## Lokálny vývoj

1. Nakopíruj `infra/.env.example` na `infra/.env` a uprav podľa potreby.
2. Spusti PostgreSQL: `docker compose -f infra/docker-compose.yml --env-file infra/.env up -d`
3. Spusti backend: `cd backend && ./mvnw spring-boot:run`
4. API beží na `http://localhost:8080`
