# =============================================================================
#  Multi-stage Dockerfile — optimised for Render free tier (512 MB / 0.1 CPU)
# =============================================================================

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Copy the Maven wrapper and POM first.
# Docker caches this layer and only re-downloads dependencies when pom.xml changes,
# making subsequent builds significantly faster.
COPY .mvn/  .mvn/
COPY mvnw   pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and produce the fat JAR
COPY src ./src
RUN ./mvnw package -DskipTests -B

# ── Stage 2: Runtime (JRE only — ~80 MB vs ~340 MB for a full JDK image) ─────
FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

# Run as a non-root user (security best practice)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy only the built JAR — nothing else from the build stage
COPY --from=builder /build/target/LoginService-0.0.1-SNAPSHOT.jar app.jar

USER appuser

# Render injects PORT automatically; app reads it via server.port=${PORT:8086}
EXPOSE 8086

# ── JVM flags tuned for Render free tier (512 MB RAM, 0.1 shared vCPU) ────────
#
#   UseContainerSupport    — auto-reads Docker memory/CPU limits (default in Java 11+, listed explicitly for clarity)
#   MaxRAMPercentage=75    — heap ceiling ≈ 384 MB, leaving ~128 MB for OS + JVM overhead + Metaspace
#   UseSerialGC            — lowest GC memory overhead; ideal for single-threaded/low-CPU containers
#   ExitOnOutOfMemoryError — hard-fail on OOM so Render restarts the container immediately
#   security.egd           — use /dev/urandom instead of blocking /dev/random;
#                            prevents multi-second startup delays caused by entropy starvation in containers
#   spring.profiles.active — explicitly activate production profile (redundant with profiles.default=prod,
#                            but being explicit avoids surprises on any platform)
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+UseSerialGC", \
            "-XX:+ExitOnOutOfMemoryError", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-Dspring.profiles.active=prod", \
            "-jar", "app.jar"]
