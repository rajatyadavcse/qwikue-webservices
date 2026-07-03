# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 – Build
#   Uses Maven + JDK 17 to compile and package the multi-module project.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy wrapper and parent pom
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Copy child module pom.xml files to allow caching of dependencies
COPY identity-service/pom.xml identity-service/pom.xml
COPY restaurant-service/pom.xml restaurant-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY qwikue-app/pom.xml qwikue-app/pom.xml

# Download all dependencies for all modules (cached unless pom files change)
RUN ./mvnw dependency:go-offline -q

# Copy all source directories
COPY identity-service/src identity-service/src
COPY restaurant-service/src restaurant-service/src
COPY order-service/src order-service/src
COPY qwikue-app/src qwikue-app/src

# Compile and package everything, skipping tests
RUN ./mvnw clean package -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 – Runtime
#   Lean JRE-only image; only the compiled qwikue-app JAR is copied in.
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine AS runtime

# Non-root user for security best practice
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy the compiled fat JAR from the builder stage (inside the qwikue-app module)
COPY --from=builder /app/qwikue-app/target/qwikue-app-0.0.1-SNAPSHOT.jar app.jar

# Render injects PORT at runtime; Spring Boot honours SERVER_PORT.
EXPOSE 8080

# Activate the production Spring profile and forward the container port.
# Render sets $PORT automatically — we pass it through SERVER_PORT.
ENTRYPOINT ["java", \
  "-Dspring.profiles.active=production", \
  "-Dserver.port=${PORT:-8080}", \
  "-jar", "app.jar"]
