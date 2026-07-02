# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 – Build
#   Uses Maven + JDK 17 to compile and package the fat JAR.
#   Nothing from this stage ends up in the final image.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy dependency manifests first so Docker can cache the layer when only
# source code changes (the slow "mvn dependency:go-offline" step is skipped).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download all dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -q

# Copy the rest of the source and build, skipping tests
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 – Runtime
#   Lean JRE-only image; only the compiled JAR is copied in.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Non-root user for security best practice
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy the fat JAR produced by the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Render injects PORT at runtime; Spring Boot honours SERVER_PORT.
# Default to 8080 so local `docker run` works without extra flags.
EXPOSE 8080

# Activate the production Spring profile and forward the container port.
# Render sets $PORT automatically — we pass it through SERVER_PORT.
ENTRYPOINT ["java", \
  "-Dspring.profiles.active=production", \
  "-Dserver.port=${PORT:-8080}", \
  "-jar", "app.jar"]
