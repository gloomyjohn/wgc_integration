# 第一阶段：构建阶段
FROM maven:3.8.4-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# 第二阶段：运行阶段 (修改这里)
# 使用 Eclipse Temurin 提供的 JDK 17，更稳定且体积依然很小
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# 3. 从构建阶段复制打包好的 jar 文件
# 注意：请确认你的 pom.xml 生成的 jar 包名称是否为 wgc-backend.jar
COPY --from=build /app/target/*.jar app.jar

# 4. 暴露我们在 application.properties 里定义的端口
EXPOSE 8080

# 5. 启动 Java 应用
# 使用 -D 选项可以确保环境变量能正确覆盖 properties 中的 localhost
ENTRYPOINT ["java", "-jar", "app.jar"]