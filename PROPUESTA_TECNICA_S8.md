# Propuesta técnica — Semana 8 (Exp3)
## Desarrollando microservicios y resiliencia en la nube con Spring Cloud

### 1. Resumen técnico del avance

Esta semana dejé el proyecto listo para correr en un entorno cloud. Hice
tres cosas nuevas y mantuve lo anterior.

Primero, reemplacé el JWT propio de S5 por **OAuth2.0**. Creé un
microservicio nuevo, `auth-server`, con Spring Authorization Server. Emite
los access tokens con el flujo `client_credentials` y los tres BFF
(`bff-web`, `bff-movil` y `bff-cajero`) pasaron a validarlos como resource
servers.

Segundo, **dockericé los 6 microservicios**: cada uno tiene su `Dockerfile`
multi-stage y su `.dockerignore`.

Tercero, armé un **`docker-compose.yaml`** que levanta los 6 en el orden
correcto. Los verifiqué con una prueba completa dentro de los contenedores.

El Circuit Breaker de S6 y la saga con JMS de S7 no cambiaron. Los volví a
probar corriendo en Docker y siguen funcionando, ahora detrás de OAuth2.


### 2. Análisis y diseño

#### 2.1 Punto de partida

Parto de la rama de S7, que ya contiene los microservicios de S6 (Eureka,
Config Server y Circuit Breaker) y la saga con JMS. La actividad pide
avanzar en continuidad, y la mensajería asíncrona es parte del sistema, así
que no tenía sentido volver atrás. Los cambios de esta semana viven en la
rama `exp3-s8-oauth2-docker`.

#### 2.2 OAuth2 con Spring Authorization Server

**Por qué un servidor aparte y por qué `client_credentials`.** En este
sistema quienes se autentican son los tres canales, no personas. No hay un
usuario final que inicie sesión, así que `authorization_code` agregaría
login y pantallas que nadie pide. Con `client_credentials` cada canal pide
su token con su identificador y su secreto. Tampoco quise dejar la emisión
de tokens dentro de `bff-web`: el punto de OAuth2 es delegar esa tarea en un
servidor propio, y así los BFF ya no necesitan compartir ninguna llave.

**Clientes y scopes.** Registré un cliente por canal, en memoria, con
`application.yml`:

| Cliente          | Secreto           | Scope    |
|------------------|-------------------|----------|
| `cliente-web`    | `WEB-KEY-2024`    | `web`    |
| `cliente-movil`  | `MOVIL-KEY-2024`  | `movil`  |
| `cliente-cajero` | `CAJERO-KEY-2024` | `cajero` |

Reutilicé las llaves de canal de S5 como secretos de cliente (con `{noop}`,
o sea sin cifrar), para no inventar credenciales nuevas. El token es un JWT
firmado con RS256 y dura 15 minutos, como el de S5. Verifiqué que `exp - iat`
sea 900 segundos (la respuesta del endpoint muestra `expires_in: 899`, que
es solo un redondeo). La versión que trae el starter es Spring
Authorization Server 1.3.2, sobre Spring Security 6.3.3.

**Cómo validan los BFF.** Cada BFF usa `spring-boot-starter-oauth2-resource-server`
y valida la firma con las claves públicas del auth-server
(`jwk-set-uri`). **No valido el emisor (`iss`)**. El `iss` que firma el
auth-server depende del host con el que se le pidió el token (en mis pruebas
locales salió `http://localhost:9000`). Dentro de Docker ese host cambia, y
si los BFF exigieran un `iss` fijo, los tokens dejarían de servir según
desde dónde se pidan. Validar solo la firma funciona igual en local y en
contenedores. La URL del JWKS no está en cada BFF, sino en el config-server:
`http://localhost:9000/oauth2/jwks` en `application.yml` y
`http://auth-server:9000/oauth2/jwks` en `application-docker.yml`.

**Reglas de acceso.** Cada BFF tiene una clase `SeguridadConfig` con su
`SecurityFilterChain`: sin sesión (STATELESS), CSRF desactivado (es una API
con token, y sin eso fallan los POST) y `/error` público:

| Ruta                                 | Servicio     | Exige                      |
|--------------------------------------|--------------|----------------------------|
| `/api/web/**`                        | `bff-web`    | `SCOPE_web`                |
| `/api/movil/**`                      | `bff-movil`  | `SCOPE_movil`              |
| `/api/cajero/**`                     | `bff-cajero` | `SCOPE_cajero`             |
| `/interno/**`, `/transferencias/**`  | `bff-web`    | cualquiera de los 3 scopes |
| cualquier otra ruta                  | los 3 BFF    | token válido               |

Exijo el scope de forma explícita, no solo la firma válida. Lo comprobé
con un token pedido **sin** parámetro `scope`: el auth-server lo emite
igual, pero sin el claim `scope`, y los BFF lo rechazan con 403.

**Errores 401 y 403.** En S5 estos errores salían del
`GlobalExceptionHandler`, porque los lanzaba un interceptor del MVC. Con
Spring Security la respuesta se produce antes de llegar al MVC, así que ese
handler ya no aplica y la respuesta quedaría vacía. Escribí en cada BFF un
`AuthenticationEntryPoint` (401) y un `AccessDeniedHandler` (403) que
devuelven el mismo JSON de siempre: `{"timestamp", "estado", "mensaje"}`.

**Reenvío del token.** `bff-movil` y `bff-cajero` llaman a `bff-web` por
`/interno/**`, que ahora también exige token. Agregué al `RestTemplate`
@LoadBalanced un `ReenvioTokenInterceptor` que toma el token de la petición
entrante y lo copia en el header `Authorization` de la llamada a `bff-web`.
Si no hay token en el contexto, la llamada sale sin header. No toqué
`BffWebClient` ni el Circuit Breaker.

**Qué retiré de S5.** Como OAuth2 reemplaza al JWT propio, borré
`JwtService`, `CanalAuthInterceptor` y `WebConfig` de los 3 BFF; `TokenController`,
sus DTOs y `CredencialesCanalInvalidasException` de `bff-web`; las excepciones
que quedaron sin uso; las dependencias `jjwt-*` de los 3 `pom.xml`; y las
claves `jwt.*` y `canal.*.llave` del config-repo. Es una decisión de alcance
a propósito, igual que cuando saqué el HTTPS en S6: el criterio de esta
semana es OAuth2, y mantener los dos mecanismos en paralelo habría dejado dos
formas de entrar al mismo sistema.

#### 2.3 Dockerización

Cada microservicio tiene el mismo `Dockerfile` (cambia solo el `EXPOSE`),
en dos etapas:

1. **Build:** `maven:3.9-eclipse-temurin-21`. Copio primero el `pom.xml` y
   bajo las dependencias, para aprovechar la caché de capas, y después
   copio `src/` y compilo con `-DskipTests`.
2. **Ejecución:** `eclipse-temurin:21-jre`, solo con el jar.

Elegí multi-stage para que quien clone el repositorio pueda correr
`docker compose up --build` sin tener Java ni Maven instalados, y sin que el
jar compilado en mi máquina se mezcle con el repositorio (`target/` está en
`.gitignore` y en `.dockerignore`).

**Configuración dentro de Docker.** El `config-repo` viaja dentro del jar
del `config-server`. Los servicios no pueden hablarse por `localhost` entre
contenedores, así que agregué un perfil `docker`
(`config-repo/application-docker.yml`) que sobrescribe dos valores: la URL de
Eureka pasa a `http://eureka-server:8761/eureka/` y el JWKS a
`http://auth-server:9000/oauth2/jwks`. Los 3 BFF arrancan con
`SPRING_PROFILES_ACTIVE=docker`, y como su `application.properties` apunta
a `localhost:8888`, el compose reemplaza esa URL con
`SPRING_CONFIG_IMPORT=optional:configserver:http://config-server:8888`.
Lo comprobé consultando al config-server: para el perfil `docker`, el valor
efectivo es el de `application-docker.yml`
(`evidencia/s8_docker/06_eureka_defaultzone.txt`).

El `config-server` es el único que cambió de dependencias: agregué
`spring-boot-starter-actuator` solo para tener `/actuator/health` como
healthcheck (por defecto solo expone `health`). Su imagen instala `curl`
porque el healthcheck lo usa.

#### 2.4 docker-compose.yaml

| Servicio        | Puerto | Depende de                                                     |
|-----------------|--------|----------------------------------------------------------------|
| `eureka-server` | 8761   | nada                                                           |
| `config-server` | 8888   | nada (con healthcheck)                                         |
| `auth-server`   | 9000   | nada                                                           |
| `bff-web`       | 8081   | `config-server` healthy, `eureka-server` y `auth-server` iniciados |
| `bff-movil`     | 8082   | igual que `bff-web`                                            |
| `bff-cajero`    | 8083   | igual que `bff-web`                                            |

El orden importa por una razón concreta: si un BFF arranca antes de que el
`config-server` responda, queda sin su configuración (su import es
`optional:`). Por eso `depends_on` espera a que el config-server esté
`healthy`. Todos los servicios llevan `restart: unless-stopped` y
`-Xmx256m`. La base H2 y el broker Artemis de la saga corren dentro de
`bff-web`, así que no necesitan contenedor. A `bff-movil` y `bff-cajero` les
agregué la variable `LOGGING_LEVEL_IO_GITHUB_RESILIENCE4J_CIRCUITBREAKER=DEBUG`
para que los cambios de estado del Circuit Breaker queden en el log y se
puedan mostrar como evidencia.

#### 2.5 Resiliencia y mensajería en el entorno cloud

**Circuit Breaker (Resilience4j).** No cambié código. En Docker detuve
`bff-web` con `docker compose stop` y llamé a `bff-movil`: las 8 llamadas
devolvieron 503 con el fallback, y las llamadas 4 a la 8 salieron como
`NOT_PERMITTED`, o sea que el circuito ya estaba abierto. Al volver a
levantar `bff-web`, `bff-movil` recuperó el 200 a los 50 segundos y el
circuito terminó en CLOSED. La secuencia completa quedó en
`evidencia/s8_docker/salida_prueba_docker.txt`:

```
04:41:58  CLOSED    -> OPEN
04:42:09  OPEN      -> HALF_OPEN
04:42:19  HALF_OPEN -> OPEN
04:42:29  OPEN      -> HALF_OPEN
04:42:39  HALF_OPEN -> OPEN
04:42:49  OPEN      -> HALF_OPEN
04:42:50  HALF_OPEN -> CLOSED
```

Los dos ciclos HALF_OPEN → OPEN ocurrieron porque esos intentos llegaron
antes de que `bff-movil` viera de nuevo a `bff-web` en su caché de Eureka.
Es el mismo comportamiento que ya había visto en S7.

**Saga con JMS.** Tampoco cambié código. Con el stack en Docker, una
transferencia 101 → 102 por 10 terminó `COMPLETADA` (saldos 5000 → 4990 y
8000 → 8010), y una 101 → 999 (destino inexistente) terminó `REVERTIDA` con
el saldo de 101 intacto. Ahora esas llamadas llevan token.

#### 2.6 Estructura final

```
banco-xyz-bff/
 |- docker-compose.yaml
 |- eureka-server/   Dockerfile, .dockerignore
 |- config-server/   Dockerfile, .dockerignore, config-repo/ (application.yml, application-docker.yml, bff-*.yml)
 |- auth-server/     Dockerfile, .dockerignore, application.yml (clientes OAuth2)   <- nuevo
 |- bff-web/         config/ (SeguridadConfig, AutenticacionEntryPoint, AccesoDenegadoHandler), interno, transferencia, ...
 |- bff-movil/       config/ (SeguridadConfig, handlers, ReenvioTokenInterceptor, RestTemplateConfig), client, ...
 |- bff-cajero/      igual que bff-movil
 |- evidencia/s8_docker, s8_oauth2, s8_capturas
```


### 3. Problemas encontrados y cómo los solucioné

**Docker no corre en mi iMac.** El iMac con el que desarrollo es Intel con
macOS 13.7.7. Según las release notes de Docker Desktop, la versión 4.48.0 fue
la última que soportó macOS 13, y desde la siguiente exige macOS 14. En vez
de actualizar el sistema a mitad de la actividad, escribí el código y los
Dockerfiles en el iMac, verifiqué ahí con `mvn` y `curl` (6 servicios como
procesos locales), y probé todo lo que usa contenedores en un Mac Apple
Silicon con Docker Desktop, clonando la rama desde GitHub. Por eso las
imágenes de la evidencia son `linux/arm64`. No probé las imágenes en amd64.

**Las transiciones del Circuit Breaker no aparecían en los logs.** La
primera corrida del script en Docker solo mostró los códigos HTTP (503 y
luego 200), porque el nivel de log por defecto no escribe los eventos del
circuito. A eso se sumaba un error mío en el script: mi `grep` buscaba
"circuitbreaker", que aparece en todas las líneas de ese logger, así que
`tail -5` habría mostrado eventos SUCCESS y no las transiciones. Lo resolví
con la variable de nivel DEBUG en el compose, filtrando por
`STATE_TRANSITION|NOT_PERMITTED`, y agregando 3 llamadas al final del
reintento: el circuito necesita 3 llamadas exitosas en HALF_OPEN para
pasar a CLOSED, y el script cortaba en la primera.

**503 en la primera llamada después de arrancar.** En la primera corrida
local con OAuth2, `bff-cajero` devolvió 503 en la primera llamada, unos 25
segundos después de arrancar. El log decía `No servers available for service:
bff-web`. No era un problema de OAuth: el servicio todavía no había descargado
el registro de Eureka, y 3 segundos después lo hizo. La misma llamada dio
200, y directo a `bff-web` con el token de cajero también. Repetí la
corrida con todo estabilizado. En Docker ahora espero 40 segundos extra
después de que el config-server queda healthy, y lo dejé indicado en el
README.

**Una llamada se bloqueó 71 segundos con `bff-web` caído.** En una de las
corridas en Docker, la primera llamada a `bff-movil` después de detener
`bff-web` tardó unos 71 segundos y terminó en `No route to host`: la caché de
Eureka todavía tenía la IP del contenedor detenido. Las dos llamadas
siguientes fallaron al instante con `No servers available`, y el circuito
se abrió en la tercera. No lo corregí porque `RestTemplateConfig` crea el
`RestTemplate` sin timeouts y cambiarlo se salía de lo que pide esta
semana. Lo dejé anotado en los riesgos.


### 4. Reflexión técnica

Al pasar del JWT de S5 a OAuth2 cambió algo más que la librería. Antes, los
tres BFF compartían una llave (`jwt.secreto`) que yo repartía por el
config-server: quien la tuviera podía emitir tokens. Ahora solo el
auth-server emite, y los BFF solo necesitan la clave pública, que no sirve
para firmar. La contrapartida es que ahora hay un servicio más del que
depende todo, y que aparecieron cosas que antes no existían, como el caché
del JWKS.

De Docker me quedó claro que "funciona en mi máquina" desaparece cuando los
servicios dejan de verse por `localhost`. Casi todo el trabajo no fue
escribir los Dockerfiles (son idénticos entre sí), sino hacer que la
configuración correcta llegara a cada contenedor: el perfil `docker`, la URL
del config-server y el orden de arranque.

Otra vez comprobé lo que ya había anotado en S5 y S6: que compile o que el
código parezca correcto no es prueba suficiente. La evidencia del Circuit
Breaker en Docker tenía dos fallas que no se veían revisando el código: el
nivel de log y mi propio `grep`. Solo aparecieron al correr el escenario
completo y mirar la salida real.


### 5. Riesgos y recomendaciones

- **Secretos en texto plano.** Los secretos de los clientes están en
  `auth-server/application.yml` con `{noop}` y repiten las llaves de canal
  de S5. En un entorno real irían cifrados (bcrypt) y fuera del repositorio,
  como variables de entorno o un gestor de secretos.
- **`/interno/**` y `/transferencias/**` aceptan cualquiera de los 3
  scopes.** Es necesario porque `bff-movil` y `bff-cajero` reenvían su
  propio token, pero significa que, por ejemplo, un token de `movil` puede
  llamar `/transferencias`. Lo correcto sería un scope dedicado para el
  tráfico interno o clientes de servicio separados.
- **La clave de firma cambia con cada reinicio del auth-server.** Genera su
  par RSA en memoria al arrancar. Lo probé: después de reiniciarlo, un token
  emitido antes siguió valiendo (200) hasta que llegó el primer token nuevo,
  porque los BFF guardan el JWKS en caché; apenas lo refrescaron con el token
  nuevo, el viejo pasó a 401. El token nuevo funcionó de inmediato
  (`evidencia/s8_oauth2/paso4_docker_oauth2.txt`, secciones d y d2). Con
  un keystore persistente, el reinicio no invalidaría los tokens vigentes.
- **Todo viaja por HTTP plano.** Los tokens se envían sin TLS entre
  servicios y hacia el cliente. Retiré el HTTPS en S6 por alcance; con OAuth2
  el costo de no tenerlo es mayor, porque un token capturado sirve hasta que
  vence (15 minutos).
- **El `RestTemplate` no tiene timeouts.** Es lo que explica la llamada de
  71 segundos con `bff-web` caído. Con timeouts de conexión y lectura,
  esa primera llamada fallaría rápido y abriría el circuito antes.
- **El estado vive en memoria.** Los saldos (CSV) y la base H2 de la saga se
  pierden al reiniciar el contenedor de `bff-web`.
- **Una sola instancia de cada servicio.** Eureka, config-server y
  auth-server son puntos únicos de falla. Eureka además muestra el aviso de
  auto-preservación por tener pocos nodos, como ya anoté en S6.
- **Todos los puertos están publicados al host.** Sirve para probar, pero en
  producción solo deberían exponerse los BFF.
- **Imágenes de 534 a 672 MB.** No las optimicé: el alcance era que fueran
  funcionales y portables.
- **Los 401 y 403 declaran `charset=ISO-8859-1`.** Hoy no afecta porque los
  mensajes no llevan tildes; si algún día las llevan, hay que fijar UTF-8 en
  esos dos handlers.
- **Deuda heredada de S7, sin cambios.** `CuentaServiceImpl.realizarRetiro()`
  sigue modificando el saldo sin la operación atómica de la saga, así que un
  retiro y una transferencia simultáneos sobre la misma cuenta podrían
  pisarse.


### 6. Repositorio GitHub

https://github.com/Zersource/banco-xyz-bff/tree/exp3-s8-oauth2-docker
