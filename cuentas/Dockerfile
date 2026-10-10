# Microservicio cuentas (8084). El puerto llega como build arg SERVICE_PORT desde docker-compose.
# /actuator/health es permitAll en su SeguridadConfig: el healthcheck no necesita token.

# Etapa 1: compilar el jar con Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Se copia primero el pom para aprovechar la cache de dependencias
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

# Etapa 2: imagen liviana solo con el jar
FROM eclipse-temurin:21-jre
ARG SERVICE_PORT
ENV SERVICE_PORT=${SERVICE_PORT}
WORKDIR /app
# curl lo usa el HEALTHCHECK
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
EXPOSE ${SERVICE_PORT}
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
  CMD curl -fs http://localhost:${SERVICE_PORT}/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java","-jar","app.jar"]
