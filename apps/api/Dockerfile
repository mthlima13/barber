# ---- Estagio 1: build ----
# Compila com a toolchain do Maven; bytecode alvo Java 21.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp -DskipTests dependency:go-offline
COPY src src
RUN mvn -B -ntp -DskipTests package

# ---- Estagio 2: execucao ----
# JVM 21 LTS, imagem minimalista.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S barberflow && adduser -S barberflow -G barberflow
USER barberflow
COPY --from=build /build/target/barberflow-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
