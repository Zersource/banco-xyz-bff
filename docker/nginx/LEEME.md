# Escala horizontal de bff-web con nginx (prueba local)

`bff-web` no guarda estado entre requests (valida cada token contra el JWKS del auth-server), así
que se puede tener más de una réplica detrás de un balanceador. `pagos` y `cuentas` no se escalan
para escrituras: guardan estado en H2 en memoria, uno por réplica.

```bash
# desde la raiz del repo, con el stack arriba (o sin el: levanta todo)
docker compose -f docker-compose.yaml -f docker-compose.escala-bff.yaml up -d
#   bff-web-1 y bff-web-2 -> localhost:8092 y 8093 (directo)
#   nginx                 -> localhost:8080 (balancea entre las dos)

# volver a una replica (bff-web otra vez en 8081, se borran nginx y bff-web-2)
docker compose up -d --scale bff-web=1 --remove-orphans
```

Requisitos: Docker Compose >= 2.24 (`ports: !override`). `deploy.replicas` lo respeta Compose v2
sin Swarm (probado con Compose 5.3.1).

## Detalles que hay que saber

a) **Las réplicas van en el override, no en `--scale`.** Un `up` que no indica la escala reconcilia
   el servicio a 1 réplica: en la primera prueba, `up -d nginx` (sin `--scale`) destruyó bff-web-2.
   Con `deploy.replicas: 2` en `docker-compose.escala-bff.yaml`, cualquier `up -d` con esos dos
   archivos mantiene las 2 réplicas.

b) **nginx resuelve el DNS al arrancar.** `server bff-web:8081` se traduce a la IP de cada réplica
   (DNS interno de Docker) solo cuando nginx inicia. Por eso nginx tiene
   `depends_on: bff-web: condition: service_healthy` (arranca cuando las 2 réplicas existen). Si una
   réplica se recrea y cambia de IP, hay que reiniciar nginx:
   `docker compose -f docker-compose.yaml -f docker-compose.escala-bff.yaml restart nginx`.

c) **nginx publica el puerto 8080 del host.** Es solo el sustituto local del balanceador: en AWS
   lo reemplaza el ALB (Application Load Balancer) con un target group que apunta a las instancias
   de bff-web, y no hace falta nginx.

d) **`zone` en el upstream.** La imagen de nginx usa `worker_processes auto` (12 workers en un
   Mac M5). Sin `zone`, cada worker lleva su propio turno de round-robin, y con pocas conexiones
   nuevas todas pueden caer en la misma réplica (se midió 10/0). Con `zone bff_web 64k;` el turno
   se comparte y el reparto es parejo (se midió 5/5). El access log de nginx incluye
   `upstream=<ip:puerto>` para ver a qué réplica fue cada request.

Evidencia: `evidencia/eft_docker/final/escala_bff_web/`.
