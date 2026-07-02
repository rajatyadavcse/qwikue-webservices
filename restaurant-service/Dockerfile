# ─────────────────────────────────────────────
# Stage 1 — Build the fat JAR with Maven
# ─────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy dependency descriptors first so Docker can cache the layer
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .

# Download all dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -q

# Copy source and build, skipping tests (tests should run in CI, not image build)
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ─────────────────────────────────────────────
# Stage 2 — Minimal JRE runtime image
# ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Non-root user for security best practice
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy only the executable JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Render (and most platforms) route traffic to port 8080 by default
EXPOSE 8080

# JVM tuning flags:
#   -XX:+UseContainerSupport   → respect cgroup CPU/memory limits (on by default in JDK 8u191+, explicit for clarity)
#   -XX:MaxRAMPercentage=75.0  → use up to 75 % of container RAM for the heap
#   -Djava.security.egd=...    → speed up SecureRandom init (common in containers)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
