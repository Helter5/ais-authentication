# ---- build stage ----
# Digest-pinned (not just tag-pinned) so a force-moved tag or registry compromise can't silently
# change what ships - same reasoning as mailpit's version pin in infra/docker-compose.yml. Re-pin
# deliberately when picking up new patches: `docker buildx imagetools inspect eclipse-temurin:21-jdk`.
FROM eclipse-temurin:24-jdk@sha256:7493205ffe6caa8074fa8a06a276bb1c5ac41d3dd0fd43a0db66d7f776e80b3e AS build
WORKDIR /build

# Zavislosti sa stiahnu do samostatnej cache vrstvy - zmena v src/ nezneplatni tuto vrstvu
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# ---- runtime stage ----
# Digest-pinned - see the build-stage comment above.
FROM eclipse-temurin:24-jre-alpine@sha256:4044b6c87cb088885bcd0220f7dc7a8a4aab76577605fa471945d2e98270741f AS runtime
WORKDIR /app

RUN addgroup -g 1001 appgroup && adduser -D -u 1001 -G appgroup appuser
COPY --from=build /build/target/*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=25s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-Duser.timezone=Europe/Bratislava", "-jar", "app.jar"]
