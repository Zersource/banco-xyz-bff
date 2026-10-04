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
# curl lo usa el healthcheck de docker-compose
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java","-jar","app.jar"]
