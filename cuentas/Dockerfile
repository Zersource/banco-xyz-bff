# Microservicio cuentas (8084). Generado desde plantillas/Dockerfile.servicio;
# el puerto llega como build arg SERVICE_PORT desde docker-compose.

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
# SeguridadConfig exige token en toda ruta (incluida /actuator/health), asi que el
# healthcheck pide un token client_credentials al auth-server y llama a health con el
# (mismo esquema que los BFF). HC_CLIENT (id:secreto) se entrega en docker-compose.
ENV HC_AUTH_URL=http://auth-server:9000
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
  CMD TOKEN=$(curl -fs -u "$HC_CLIENT" -d grant_type=client_credentials "$HC_AUTH_URL/oauth2/token" \
        | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p') \
      && curl -fs -H "Authorization: Bearer $TOKEN" http://localhost:${SERVICE_PORT}/actuator/health | grep -q '"status":"UP"' \
      || exit 1
ENTRYPOINT ["java","-jar","app.jar"]
