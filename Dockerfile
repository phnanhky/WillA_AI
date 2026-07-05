### Multi-stage build for Spring Boot (JDK 21)

# 1) Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Tải dependencies trước (layer này chỉ rebuild khi pom.xml đổi).
# Cache mount giữ ~/.m2 giữa các lần build -> không tải lại thư viện.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -DskipTests dependency:go-offline

# Copy source sau khi đã cache dependencies
COPY src ./src

# Build (skip tests cho nhanh; cache mount giúp không tải lại thư viện)
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -DskipTests package

# 2) Runtime stage
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN apk add --no-cache wget

# Optional: set timezone
ENV TZ=Asia/Ho_Chi_Minh

# Runtime env từ docker-compose env_file — không bake secrets vào image
COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Expose application port
EXPOSE 8080

# Spring Boot khởi động chậm trên VPS — start-period dài
HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=5 \
  CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1

# Default JVM opts — VPS nhỏ: tránh OOM khi chạy cùng Elasticsearch
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Xms512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]