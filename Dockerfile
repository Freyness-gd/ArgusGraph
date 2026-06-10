# ── Build stage ────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
# Warm the dependency cache before copying sources, so code-only changes skip re-download.
COPY gradle ./gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
RUN ./gradlew --no-daemon dependencies -q || true
COPY . .
RUN ./gradlew --no-daemon bootJar -x test

# ── Runtime stage ────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
COPY --from=build /build/build/libs/*.jar app.jar
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=dev
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8080/api/v1/actuator/health/readiness || exit 1
USER nobody
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
