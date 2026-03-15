### Multi-stage build for Spring Boot (JDK 21)

# 1) Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy Maven descriptor first for better layer caching
COPY pom.xml .
# Copy source
COPY src ./src

# Build the application (skip tests for speed; change to `-DskipTests=false` if needed)
RUN mvn -q -DskipTests package

# 2) Runtime stage
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Optional: set timezone
ENV TZ=Asia/Ho_Chi_Minh

# Copy built jar from build stage
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Explicitly copy .env if we need it in root directory inside container
COPY .env .env

# Expose application port
EXPOSE 8080

# Healthcheck (optional, adjust path if needed)
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

# Default JVM opts (tune as needed)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]