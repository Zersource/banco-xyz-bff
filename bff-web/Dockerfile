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
WORKDIR /app
# curl lo usa el HEALTHCHECK
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
# SeguridadConfig exige token en toda ruta (incluida /actuator/health), asi que el
# healthcheck pide un token client_credentials al auth-server y llama a health con el.
# De paso comprueba que el JWKS se resuelve entre contenedores.
# HC_CLIENT (id:secreto) se entrega en docker-compose, no queda dentro de la imagen.
ENV HC_AUTH_URL=http://auth-server:9000
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
  CMD TOKEN=$(curl -fs -u "$HC_CLIENT" -d grant_type=client_credentials "$HC_AUTH_URL/oauth2/token" \
        | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p') \
      && curl -fs -H "Authorization: Bearer $TOKEN" http://localhost:8081/actuator/health | grep -q '"status":"UP"' \
      || exit 1
ENTRYPOINT ["java","-jar","app.jar"]
