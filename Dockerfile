# CRYD 多智能体协同学习平台 — Docker 多阶段构建
# DeepSeek V4 Pro + Spring Boot 3.5 + Java 21

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY 后端/pom.xml .
RUN mvn dependency:go-offline -B -q
COPY 后端/src ./src
RUN mvn package -DskipTests -B -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/data /app/uploads

EXPOSE 8080
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
