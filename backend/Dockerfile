# Multi-stage build: the toolchain never reaches the runtime image.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies are resolved in their own layer so a source-only change does not re-download
# the world on every rebuild.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---------------------------------------------------------------------------

FROM eclipse-temurin:21-jre-alpine AS runtime

# Run as an unprivileged user. A container process that does not need root should not have it.
RUN addgroup -S casino && adduser -S -G casino casino

WORKDIR /app
COPY --from=build /build/target/casino-backend-*.jar app.jar
RUN chown -R casino:casino /app

USER casino
EXPOSE 8080

# Actuator health is the readiness signal Compose waits on.
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# Containers get a memory cap from the runtime, so let the JVM size itself against that
# rather than against the host's total RAM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
