# Guia de despliegue en la nube — Banco XYZ (EFT)

Repositorio: `https://github.com/Zersource/banco-xyz-bff` — rama `eft-final`

Esta guia describe los pasos para desplegar el ecosistema completo (10 servicios + Kafka) en una instancia EC2 de AWS usando Docker Compose, partiendo desde cero.

---

## 1. Requisitos de la instancia EC2

| Parametro | Valor recomendado |
|---|---|
| Tipo de instancia | `t3.large` (2 vCPU, 8 GB RAM) o superior |
| Sistema operativo | Amazon Linux 2023 / Ubuntu 22.04 LTS |
| Almacenamiento | 30 GB gp3 (imagenes Docker + build) |
| Security Group | Inbound: SSH (22), puertos 8081-8086, 8761, 8888, 9000 |

El stack completo consume ~3.9 GB de RAM en reposo (10 contenedores Java + Kafka). Con 8 GB queda margen para el build y el sistema operativo.

---

## 2. Instalar Docker y Docker Compose en la EC2

### Amazon Linux 2023

```bash
sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable docker --now
sudo usermod -aG docker $USER

# Compose v2 (plugin de Docker CLI)
DOCKER_CONFIG=${DOCKER_CONFIG:-$HOME/.docker}
mkdir -p $DOCKER_CONFIG/cli-plugins
curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-$(uname -m) \
  -o $DOCKER_CONFIG/cli-plugins/docker-compose
chmod +x $DOCKER_CONFIG/cli-plugins/docker-compose

# Cerrar sesion y volver a entrar para que el grupo docker aplique
exit
```

### Ubuntu 22.04

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin git
sudo usermod -aG docker $USER
exit
```

Verificar despues de reconectar:

```bash
docker --version
docker compose version   # debe ser >= 2.24
```

---

## 3. Clonar el repositorio

```bash
git clone --branch eft-final https://github.com/Zersource/banco-xyz-bff.git
cd banco-xyz-bff
```

---

## 4. Validar la configuracion

```bash
docker compose config -q
# Sin salida, exit 0 → configuracion valida
```

---

## 5. Construir y levantar el stack

```bash
docker compose up -d --build
```

El primer build en la EC2 (sin cache) tarda ~2 minutos: descarga las imagenes base (`maven:3.9-eclipse-temurin-21`, `eclipse-temurin:21-jre-alpine`, `apache/kafka:3.7.0`), compila cada modulo con Maven y construye las 11 imagenes.

Verificar que los 10 servicios estan `healthy`:

```bash
# Esperar ~60 segundos y luego:
docker compose ps
```

Todos deben mostrar `Up ... (healthy)`. El orden de arranque esta controlado por `depends_on` con `service_healthy`:

```
config-server (primero)
  └─ eureka-server
       └─ auth-server ─┬─ cuentas ─┬─ bff-web
          kafka ────────┤  clientes ─┤  bff-movil
                        └─ pagos    └─ bff-cajero
```

---

## 6. Verificar con la prueba end-to-end

```bash
MODO=docker /bin/bash ./prueba-e2e.sh
```

Esperado: `95/95 verificaciones OK`, exit 0. Si alguna falla en los primeros segundos, Eureka puede necesitar mas tiempo para propagar los registros (~90 s). Reintentar.

---

## 7. Probar el batch

```bash
docker compose --profile batch run --rm -e JOB=todos batch
```

Los 3 jobs deben finalizar con `EXITO`.

---

## 8. Escala horizontal (opcional, demostracion)

Para escalar bff-web a 2 replicas con nginx como balanceador:

```bash
docker compose -f docker-compose.yaml -f docker-compose.escala-bff.yaml up -d
```

Esto levanta 2 instancias de bff-web (puertos 8092 y 8093) y un nginx en el puerto 8080 que distribuye las peticiones con round-robin.

Para volver a la configuracion normal:

```bash
docker compose up -d --scale bff-web=1 --remove-orphans
```

---

## 9. Configuracion del Security Group (AWS)

Para acceso desde fuera de la EC2, abrir los puertos necesarios en el Security Group:

| Puerto | Protocolo | Origen | Servicio |
|---|---|---|---|
| 22 | TCP | Mi IP | SSH |
| 8081 | TCP | Mi IP | bff-web |
| 8082 | TCP | Mi IP | bff-movil |
| 8083 | TCP | Mi IP | bff-cajero |
| 9000 | TCP | Mi IP | auth-server (obtener tokens) |
| 8761 | TCP | Mi IP | Eureka dashboard (opcional) |
| 8888 | TCP | Mi IP | Config Server (opcional) |

En produccion solo se exponen los BFF y el auth-server; Eureka, Config Server, Kafka, cuentas, clientes y pagos quedan internos.

---

## 10. Probar desde fuera de la EC2

Reemplazar `<EC2-IP>` por la IP publica de la instancia:

```bash
# Obtener token
TOKEN=$(curl -s -u cliente-web:WEB-KEY-2024 \
  -d grant_type=client_credentials -d scope=web \
  http://<EC2-IP>:9000/oauth2/token \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')

# Consultar cuentas
curl -H "Authorization: Bearer $TOKEN" http://<EC2-IP>:8081/api/web/cuentas
```

---

## 11. Detener el stack

```bash
# Detener sin borrar volumenes (los datos persisten)
docker compose down

# Detener y borrar volumenes (limpieza total)
docker compose down -v --remove-orphans
```

---

## 12. Consideraciones para produccion

Estas mejoras estan fuera del alcance de la EFT pero se documentan como propuesta:

1. **Base de datos persistente.** Reemplazar H2 en memoria por PostgreSQL (RDS) en `cuentas` y `pagos` para que los saldos y transferencias sobrevivan reinicios.
2. **Secretos.** Mover los secretos OAuth2 (`WEB-KEY-2024`, etc.) a AWS Secrets Manager o un `.env` excluido del repositorio.
3. **HTTPS.** Colocar un Application Load Balancer (ALB) con certificado ACM delante de los BFF.
4. **Clave RSA persistente.** El auth-server genera su par de claves en memoria al arrancar; al reiniciarlo, los tokens en cache quedan invalidos por hasta 15 minutos. Persistir la clave en un volumen o en Secrets Manager.
5. **Kafka gestionado.** Reemplazar el Kafka del compose por Amazon MSK para alta disponibilidad y replicacion.
6. **Orquestacion.** Para multiples instancias EC2, migrar a ECS Fargate o EKS con los mismos Dockerfiles.
