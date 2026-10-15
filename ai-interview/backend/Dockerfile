# 多阶段构建（计划 §10.4）：Maven 构建层缓存依赖 → JRE 运行层
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
# 阿里云镜像先行 COPY：settings 变化才使依赖层缓存失效
COPY docker-maven-settings.xml /root/.m2/settings.xml
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
# 测试已在宿主机 mvn verify 执行（M6-6.1），镜像构建跳过以加速并解耦测试库依赖
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]
