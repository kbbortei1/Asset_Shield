# ── build stage ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
# go-offline is a best-effort layer-cache warm-up; it can fail to pre-resolve
# certain optional/transitive artifacts (a known Maven quirk) — don't let that
# abort the build. The non-offline `package` below fetches anything missing.
RUN mvn -q -B dependency:go-offline || true
COPY src ./src
RUN mvn -q -B package -DskipTests

# ── runtime stage ───────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 1001 --user-group appuser
USER appuser
WORKDIR /app
COPY --from=build /workspace/target/gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
