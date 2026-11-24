# syntax=docker/dockerfile:1

# ---------- Etapa 1: construcción ----------
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /workspace

# Primero solo lo necesario para resolver dependencias: esta capa se cachea
# mientras no cambien pom.xml ni el wrapper.
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

# Después el código fuente.
COPY src src
RUN ./mvnw -q -B package -DskipTests \
    && cp target/nutrisport-backend-*.jar app.jar

# ---------- Etapa 2: ejecución ----------
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S nutrisport && adduser -S -G nutrisport -H -s /sbin/nologin nutrisport

WORKDIR /app
COPY --from=build --chown=nutrisport:nutrisport /workspace/app.jar /app/app.jar

USER nutrisport

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
