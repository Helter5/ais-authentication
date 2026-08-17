# ---- build stage ----
# Digest-pinned (not just tag-pinned) so a force-moved tag or registry compromise can't silently
# change what ships - same reasoning as mailpit's version pin in infra/docker-compose.yml. Re-pin
# deliberately when picking up new patches: `docker buildx imagetools inspect eclipse-temurin:21-jdk`.
FROM eclipse-temurin:25-jdk@sha256:c42fecf62f32725c65cfea284c012526d6fb31cc78123c740ebdc1cfd2dced12 AS build
WORKDIR /build

# Zavislosti sa stiahnu do samostatnej cache vrstvy - zmena v src/ nezneplatni tuto vrstvu
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# ---- runtime stage ----
# Digest-pinned - see the build-stage comment above.
FROM eclipse-temurin:25-jre-alpine@sha256:28db6fdf60e38945e43d840c0333aeaec66c15943070104f7586fd3c9d1665b0 AS runtime
WORKDIR /app

RUN addgroup -g 1001 appgroup && adduser -D -u 1001 -G appgroup appuser
COPY --from=build /build/target/*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=25s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-Duser.timezone=Europe/Bratislava", "-jar", "app.jar"]
