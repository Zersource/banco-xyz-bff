# Banco XYZ - BFF (Exp2 S5)

Implementacion del patron **Backend for Frontend (BFF)** sobre los datos
legacy del Banco XYZ (`KariVillagran/bank_legacy_data`), como parte de la
Experiencia 2, Semana 5 de Desarrollo Backend III. Continua directamente
el proyecto de Exp2 S4, agregando autenticacion con JWT, autorizacion por
canal y HTTPS.


-- Objetivo --

Exponer 3 backends independientes, cada uno adaptado al cliente que lo
utiliza:

- **BFF Web** (`/api/web`): datos completos de cada cuenta, pensado para
  una interfaz de escritorio.
- **BFF Movil** (`/api/movil`): datos livianos, solo lo esencial para
  minimizar el volumen de la respuesta.
- **BFF Cajero** (`/api/cajero`): solo las operaciones criticas (consulta
  de saldo y retiro), sin datos personales.

 Ver `PROPUESTA_TECNICA.md` para el detalle de la estrategia elegida, 
 la justificación, y los cambios efectuados respecto a S4.


-- Como ejecutar --

```bash
mvn spring-boot:run
```

La aplicacion levanta en `https://localhost:8443` (HTTPS con certificado
autofirmado). Al ser autofirmado, curl y el navegador van a marcarlo como
no confiable; para pruebas con curl se usa la opcion `-k`.

## Autenticacion y autorizacion por canal (JWT)

A diferencia de S4 (donde la llave viajaba en texto plano en cada
request), ahora cada canal primero pide un token, y despues lo usa en
todas sus llamadas al BFF que le corresponde.


-- 1. Pedir el token --

```bash
curl -sk -X POST https://localhost:8443/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"canal": "WEB", "clave": "WEB-KEY-2024"}'
```

Aquí tenemos las llaves disponibles por cada canal (mismas de S4, ahora usadas solo para
obtener el token, no en cada request):

| Canal  | Clave            |
|--------|------------------|
| Web    | WEB-KEY-2024     |
| Movil  | MOVIL-KEY-2024   |
| Cajero | CAJERO-KEY-2024  |

La respuesta trae el token, el canal y los minutos de expiracion:

```json
{"token": "eyJhbGciOiJIUzI1NiJ9...", "canal": "WEB", "expiraEnMinutos": 15}
```


-- 2. Usar el token --

Todas las llamadas al BFF correspondiente van con el token en el header
`Authorization: Bearer <token>`:

```bash
curl -sk https://localhost:8443/api/web/cuentas \
  -H "Authorization: Bearer <token>"
```




 -- Codigos de error -- 

- **401 Unauthorized**: no se pudo autenticar (falta el token, esta mal
  formado, vencido o mal firmado).
- **403 Forbidden**: el token es valido, pero es de otro canal (ej. usar
  un token de MOVIL contra `/api/web/**`).

 -- Endpoints --

### Autenticacion (publico, sin token)
- `POST /api/auth/token` - recibe `{canal, clave}`, devuelve el JWT

### BFF Web (requiere token de canal WEB)
- `GET /api/web/cuentas` - lista todas las cuentas con detalle completo
- `GET /api/web/cuentas/{cuentaId}` - detalle completo de una cuenta
- `GET /api/web/transacciones` - transacciones generales del banco

### BFF Movil (requiere token de canal MOVIL)
- `GET /api/movil/cuentas/{cuentaId}` - resumen liviano de la cuenta

### BFF Cajero (requiere token de canal CAJERO)
- `GET /api/cajero/cuentas/{cuentaId}/saldo` - consulta de saldo
- `POST /api/cajero/cuentas/{cuentaId}/retiro` - realiza un retiro
