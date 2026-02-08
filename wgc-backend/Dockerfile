# Spring Boot 构建和运行 - 多阶段 Dockerfile

# ========================= 第一阶段：构建 =========================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# ========================= 第二阶段：运行 =========================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 安装健康检查所需的工具
RUN apk add --no-cache curl

# 从构建阶段复制编译好的 JAR
COPY --from=build /app/target/*.jar app.jar

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=10s --timeout=5s --retries=5 --start-period=30s \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["java", "-Dserver.port=8080", "-jar", "app.jar"]
