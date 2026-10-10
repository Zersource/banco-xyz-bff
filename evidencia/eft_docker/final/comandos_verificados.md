# Comandos verificados desde un clon limpio

Probado el 2026-10-10 en un clon nuevo de `eft-docker-3` (`git clone --branch eft-docker-3
https://github.com/Zersource/banco-xyz-bff.git /tmp/clean-clone`, commit `defdf27`), sin `.env`
ni overrides locales. Cada comando se corrió desde la raíz del clon, en este orden.

## Requisitos previos

- Docker Engine 29.6.2 y Docker Compose v5.3.1 (probado). Mínimo: **Compose >= 2.24**, porque los
  overrides de escala usan `!override`. Se comprueba con `docker compose version`.
- Memoria asignada a Docker: con 7.75 GiB alcanza. El stack completo usa ~3.9 GiB en reposo
  (10 contenedores).
- Puertos libres en el host: 8081–8086, 8761, 8888, 9000 y 9094. Las pruebas de escala usan
  además 8080, 8090–8093.
- Para el e2e: `bash` (sirve el 3.2 de macOS), `curl` y `python3`.
- Arquitectura: ningún Dockerfile ni compose fija `platform`. Las imágenes se construyen en la
  máquina donde se ejecutan; se probó en arm64 (Mac M5) y las imágenes base tienen variante amd64
  para la EC2.

## Comandos (en orden) y salida esperada

| # | Comando | Salida esperada |
|---|---|---|
| 1 | `docker compose config -q` | Sin salida, exit 0 |
| 2 | `docker compose up -d --build` | exit 0; en ~30 s (con caché de build) los 10 contenedores quedan `healthy` |
| 3 | `docker compose ps` | 10 servicios `Up … (healthy)`: config-server, eureka-server, auth-server, kafka, cuentas, clientes, pagos, bff-web, bff-movil, bff-cajero |
| 4 | `MODO=docker /bin/bash ./prueba-e2e.sh` | `RESUMEN: 95/95 verificaciones OK (modo docker, secciones omitidas: 0)`, exit 0 (~3 min) |
| 5 | `docker compose --profile batch run --rm -e JOB=todos batch` | exit 0; log con los 3 Jobs `finalizado con EXITO` |
| 6 | `docker compose -f docker-compose.yaml -f docker/batch/test/compose.falla-permanente.yaml --profile batch up batch` | 4 intentos `finalizado con ESTADO FAILED`; el propio `up` devuelve 0 |
| 7 | `docker inspect -f '{{.State.ExitCode}} {{.RestartCount}}' $(docker compose -f docker-compose.yaml -f docker/batch/test/compose.falla-permanente.yaml --profile batch ps -aq batch)` | `1 3` |
| 8 | `docker compose --profile batch stop batch` | Detiene el contenedor de la prueba anterior |
| 9 | `/bin/bash docker/batch/test/recuperar_csv.sh` | Última línea: `[script] ExitCode RestartCount: 0 1` |
| 10 | `docker compose down -v --remove-orphans` | Borra contenedores, red y volúmenes |

Notas:
- **`run` frente a `up`:** `docker compose run` no aplica la política `restart`. Las pruebas de
  reintento del batch van con `up`, y su resultado se lee con `docker inspect`, no con el exit
  code de `up`.
- **Comandos `rm` en las pruebas del batch:** `recuperar_csv.sh` ya hace `docker compose rm -fsv
  batch` antes de empezar. Si se repite la prueba permanente, conviene correr antes `docker
  compose --profile batch rm -fsv batch` para que `RestartCount` empiece en 0.

## Pruebas opcionales (verificadas en la copia de trabajo, no en el clon)

| Comando | Salida esperada |
|---|---|
| `docker compose -f docker-compose.yaml -f docker-compose.escala-bff.yaml up -d` | bff-web-1, bff-web-2 (8092/8093) y nginx (8080); 2 instancias BFF-WEB en Eureka tras ~60 s |
| `docker compose up -d --scale bff-web=1 --remove-orphans` | Vuelve a 1 réplica en 8081; nginx y bff-web-2 se eliminan |
| `docker compose -f docker-compose.yaml -f docker-compose.escala.yaml up -d --scale cuentas=2` | 2 réplicas de cuentas (8090/8091). **Rompe el e2e** (cuentas deja 8084); volver con `docker compose up -d --scale cuentas=1` |
| `docker compose -f docker-compose.yaml -f docker/batch/compose.postgres.yaml --profile batch run --rm -e JOB=todos batch` | exit 0 con el perfil `postgres` |
