# Batch de Banco XYZ (Spring Batch). Vive fuera de batch/ porque esa carpeta la
# importa otro equipo; el compose lo usa con context ./batch.
# Proceso que termina (no es un servidor): sin HEALTHCHECK. El exit code del Job
# (0 = COMPLETED, 1 = otro estado) es lo que usa restart: on-failure.

# Etapa 1: compilar el jar con Maven (Java 21, segun <java.version> del pom)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Se copia primero el pom para aprovechar la cache de dependencias
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package \
 && cp target/banco-xyz-batch-*.jar /build/batch.jar

# Etapa 2: solo el JRE y el jar, con usuario sin privilegios
FROM eclipse-temurin:21-jre
RUN groupadd --system batch && useradd --system --gid batch --home-dir /app batch \
 && mkdir -p /app/data && chown -R batch:batch /app
WORKDIR /app
COPY --from=build --chown=batch:batch /build/batch.jar /app/batch.jar
USER batch
# H2 (perfil dev) crea ./data/bancoxyz.mv.db relativo a WORKDIR -> /app/data
# (el volumen nombrado lo monta docker-compose)
# Forma shell para resolver JOB dentro del contenedor; exec deja a java como PID 1
# y propaga su exit code a Docker.
ENTRYPOINT ["sh","-c","exec java -jar /app/batch.jar \"${JOB:-transacciones}\""]
