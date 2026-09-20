# Banco XYZ - Microservicios con Spring Cloud (Exp3 S6)

  Esta es la continuacion directa del BFF de Exp2 S5. Esta semana el monolito
con 3 fachadas (bff.web/bff.movil/bff.cajero) se separa en 5 microservicios
independientes, cada uno con su propio proceso, coordinados con Spring Cloud:
Config Server, Eureka para el Service Discovery, y Circuit Breaker con
Resilience4j.

  Ver PROPUESTA_TECNICA.md para el detalle del diseno, las decisiones que
tome y los problemas que me fui encontrando en el camino.


-- Modulos --

| Modulo          | Puerto | Rol                                                            |
|-----------------|--------|-----------------------------------------------------------------|
| `eureka-server` | 8761   | Service Discovery. No depende de nadie mas.                     |
| `config-server` | 8888   | Configuracion centralizada (backend `native`, carpeta `config-repo/`). |
| `bff-web`       | 8081   | Unico dueno de los datos (CSV en memoria). Emite y valida JWT.  |
| `bff-movil`     | 8082   | Ya no tiene datos propios: le pide todo a `bff-web` via Eureka. |
| `bff-cajero`    | 8083   | Igual que movil: saldo y retiro via `bff-web`.                  |

  bff-movil y bff-cajero llaman a bff-web a traves de /interno/** (no pensado
para clientes externos), protegidos con Circuit Breaker: si bff-web cae,
responden 503 en vez de quedarse esperando.


-- Como ejecutar --

  Hay que levantarlos en orden (cada uno en su propia terminal, esperando que
el anterior este arriba antes de tirar el siguiente):

```bash
cd eureka-server && mvn spring-boot:run     # esperar "Started EurekaServerApplication"
cd config-server && mvn spring-boot:run     # esperar "Started ConfigServerApplication"
cd bff-web        && mvn spring-boot:run    # esperar "Started BancoXyzBffApplication"
cd bff-movil      && mvn spring-boot:run    # esperar "Started BffMovilApplication"
cd bff-cajero     && mvn spring-boot:run    # esperar "Started BffCajeroApplication"
```

  Dashboard de Eureka: `http://localhost:8761` (deberian aparecer BFF-WEB,
BFF-MOVIL y BFF-CAJERO, los tres en UP).

  Nota: a diferencia de S5, esta semana los servicios corren sobre HTTP plano,
sin el certificado autofirmado (ver PROPUESTA_TECNICA.md, seccion de riesgos,
para la justificacion de por que se saco).


## Autenticacion y autorizacion por canal (JWT)

  Se mantiene igual que en S5: bff-web es el unico que emite tokens.
bff-movil y bff-cajero solo los validan, con la misma llave HMAC compartida
via config-server.

-- 1. Pedir el token (siempre contra bff-web, sin importar el canal) --

```bash
curl -s -X POST http://localhost:8081/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"canal": "MOVIL", "clave": "MOVIL-KEY-2024"}'
```

| Canal  | Clave            |
|--------|------------------|
| Web    | WEB-KEY-2024     |
| Movil  | MOVIL-KEY-2024   |
| Cajero | CAJERO-KEY-2024  |

-- 2. Usar el token contra el BFF que corresponde --

```bash
curl -s http://localhost:8082/api/movil/cuentas/101 \
  -H "Authorization: Bearer <token>"
```

-- Codigos de error --

- **401 Unauthorized**: no se pudo autenticar (falta el token, esta mal
  formado, vencido o mal firmado).
- **403 Forbidden**: el token es valido, pero es de otro canal.
- **404 Not Found**: la cuenta no existe (respuesta real de `bff-web`, no
  pasa por el Circuit Breaker).
- **503 Service Unavailable**: `bff-web` no respondio (caido, timeout, o el
  circuito esta abierto). Solo aparece ante una falla real de
  infraestructura, no ante un error de negocio como el 404 de arriba.


-- Endpoints --

### Autenticacion (publico, sin token)
- `POST /api/auth/token` (bff-web) - recibe `{canal, clave}`, devuelve el JWT

### BFF Web (requiere token de canal WEB)
- `GET /api/web/cuentas` - lista todas las cuentas con detalle completo
- `GET /api/web/cuentas/{cuentaId}` - detalle completo de una cuenta
- `GET /api/web/transacciones` - transacciones generales del banco

### BFF Movil (requiere token de canal MOVIL)
- `GET /api/movil/cuentas/{cuentaId}` - resumen liviano de la cuenta

### BFF Cajero (requiere token de canal CAJERO)
- `GET /api/cajero/cuentas/{cuentaId}/saldo` - consulta de saldo
- `POST /api/cajero/cuentas/{cuentaId}/retiro` - realiza un retiro

### Interno (solo bff-movil/bff-cajero -> bff-web, sin token)
- `GET /interno/cuentas/{cuentaId}`
- `GET /interno/transacciones/ultimas?cantidad=5`
- `GET /interno/cuentas/{cuentaId}/saldo`
- `POST /interno/cuentas/{cuentaId}/retiro?monto=`
