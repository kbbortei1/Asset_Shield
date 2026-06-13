# ── build stage ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
# Cache warmer only — transient registry/DNS hiccups must not fail the build;
# the package step below fetches anything still missing.
RUN mvn -q -B dependency:go-offline || true
COPY src ./src
RUN mvn -q -B package -DskipTests

# ── runtime stage ───────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 1001 --user-group appuser \
 && mkdir -p /data/storage \
 && chown appuser:appuser /data/storage
USER appuser
WORKDIR /app
COPY --from=build /workspace/target/damage-service-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
