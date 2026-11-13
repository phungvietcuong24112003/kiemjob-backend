# ===================================================================
# Multi-stage Dockerfile for KiemJob Backend (Spring Boot 4 / Java 21)
# ===================================================================

# ── Stage 1: Build stage ──────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Cài đặt bash và dos2unix để xử lý file script Maven Wrapper trên Linux
RUN apk add --no-cache dos2unix bash

# Copy file cấu hình dependency để tận dụng Docker layer cache
COPY mvnw pom.xml ./
COPY .mvn .mvn

RUN dos2unix mvnw && chmod +x mvnw
RUN ./mvnw dependency:go-offline -B -q || true

# Copy toàn bộ mã nguồn và build package (bỏ qua test trong image build)
COPY src src
RUN ./mvnw clean package -DskipTests -B

# Trích xuất Spring Boot layertools (nếu cần) hoặc lấy trực tiếp jar
RUN cp target/*.jar app.jar

# ── Stage 2: Runtime stage ─────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Tạo non-root user và group để tuân thủ nguyên tắc Least Privilege
RUN addgroup -S spring && adduser -S spring -G spring

# Copy file jar từ builder stage
COPY --from=builder --chown=spring:spring /workspace/app.jar app.jar

# Chạy dưới quyền non-root user
USER spring:spring

# Expose cổng mặc định của Spring Boot
EXPOSE 8080

# Cấu hình JVM tối ưu cho Docker / ECS Fargate container (nhận biết cgroups giới hạn RAM)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

