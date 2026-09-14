# Propuesta Tecnica - Exp2 S5: BFF con autenticacion, autorizacion y HTTPS

--- Contexto ---  Bitácora

Esta semana la actividad pide continuar el proyecto de Exp2 S4 (patron
Backend for Frontend sobre los datos del Banco XYZ), agregando lo que en
S4 quedo pendiente y documentado como riesgo: HTTPS, y una autenticacion
y autorizacion real por canal, en vez de una llave estatica en texto
plano.

La estrategia de BFF (backends independientes por cliente: `bff.web`,
`bff.movil`, `bff.cajero`) y la capa de datos comun se mantienen tal cuál, 
que en S4. Este documento se enfoca en lo nuevo.

--- Que cambia respecto a S4

--- Autenticacion: de llave estatica a JWT ---

En S4, cada request llevaba el header `X-Canal-Key` con una llave fija
(`WEB-KEY-2024`, etc.), comparada como texto plano en cada llamada. Eso
demostraba el concepto de "un canal, una llave", pero como quedo anotado
en los riesgos de S4, no era un mecanismo de autenticacion real.

Para S5 agregue un endpoint publico `POST /api/auth/token` (`TokenController`)
donde un canal envia su nombre y su llave (las mismas de S4), y si
coinciden recibe un JWT firmado (HMAC-SHA256, `JwtService`) valido por 15
minutos, con un claim `canal` que identifica para que BFF sirve. Ese
token es el que se manda despues en cada llamada, como
`Authorization: Bearer <token>`.

Elegi JWT y no Spring Security completo por la misma razon que en S4: la
actividad pide "gestionar autenticacion y autorizacion especifica por
canal", no un sistema de identidad con usuarios reales ni roles
complejos. Un JWT con un solo claim (`canal`) resuelve exactamente eso,
sin agregar un framework completo de seguridad que esta fuera del
alcance del curso.

--- Autorizacion separada de autenticacion

El `CanalAuthInterceptor` ahora distingue dos fallos que en S4 devolvian
el mismo codigo:

- **401 (autenticacion)**: el token no vino, esta corrupto, mal firmado o
  vencido — no se pudo confirmar quien llama. Se traduce a `TokenInvalidoException`.
- **403 (autorizacion)**: el token es valido y pertenece a un canal real,
  pero no es el canal de la ruta solicitada (ej. un token de MOVIL
  llamando a `/api/web/**`). Se traduce a `AccesoCanalNoAutorizadoException`,
  que ahora indica ambos canales en el mensaje.

Esta separacion es la diferencia concreta entre "no se quien eres" y "se
quien eres, pero no puedes entrar aca", que es lo que pide el criterio de
la pauta al hablar de autenticacion Y autorizacion como cosas distintas.

--- HTTPS

Genere un certificado autofirmado PKCS12 (`keystore/bff-keystore.p12`,
valido 10 anios, alias `bff-banco-xyz`) y configure
`server.ssl.*` en `application.properties` para que el servidor levante
en `https://localhost:8443`. Al ser autofirmado, el navegador y curl lo
marcan como no confiable — es esperable a nivel de actividad de curso, y
las pruebas con curl usan `-k` para omitir esa validacion. No compre un
certificado de una CA real porque no aplica para un proyecto que corre en
localhost sin dominio propio.

--- Optimizacion de respuestas y consumo de recursos por canal

Esto ya venía en parte desde S4 y lo dejo explicito aca
porque la pauta de S5 lo pide:

- Los 3 repositorios (`CuentaRepository`, `MovimientoRepository`,
  `TransaccionRepository`) cargan los CSV una sola vez al levantar la
  aplicacion (`@PostConstruct`), y quedan en memoria como `Map`/`List`.
  No hay lectura de disco por request, en ningun canal.
- La diferencia de tamaño de payload por canal es real, no solo de
  nombre de clase: Web devuelve el historial completo de movimientos por
  cuenta, Movil solo las ultimas 5 transacciones simplificadas y sin
  datos personales, Cajero solo el saldo o el resultado del retiro.
 
Medido con el servidor levantado, para la cuenta 101 (curl -s -o /dev/null -w '%{size_download}'):

| Canal  | Tamaño de respuesta |
|--------|----------------------|
| Web    | 280 bytes            |
| Movil  | 340 bytes            |
| Cajero | 31 bytes             |

Cajero es, como se esperaba, el más liviano por lejos: solo devuelve 
saldo o el resultado del retiro. El caso de Web vs Movil merece 
una aclaración, que dejo detallada en "Problemas encontrados y 
cómo los resolví": en este dataset de prueba Movil da más pesado 
que Web, pero es un efecto del tamaño del dataset 
(pocas cuentas, poco historial), no del diseño.
Con datos reales esta relación se invertiría sin problema.

--- Como quedó la estructura del proyecto (actualizada) ---

```
com.duoc.bancoxyzbff
 |- model            (Cuenta, MovimientoAnual, Transaccion)
 |- repository       (carga de CSV en memoria)
 |- service          (interfaz + impl, logica comun a los 3 BFF)
 |- auth             (JwtService, TokenController, DTOs de token)      <- nuevo en S5
 |- bff.web          (controller + dto)
 |- bff.movil        (controller + dto)
 |- bff.cajero       (controller + dto)
 |- config           (interceptor de autenticacion/autorizacion + WebConfig)
 |- exception        (manejo de errores, incluye TokenInvalidoException
                       y CredencialesCanalInvalidasException)          <- nuevo en S5
```

--- Problemas encontrados y como los resolvi ---

La compilación no me dio problemas esta vez (mvn clean compile corrió limpio a la primera, 
igual que en S4), así que no hubo bugs de código que resolver. Lo que sí me hizo detenerme 
un rato fue algo que encontré al medir el tamaño de las respuestas por canal: la del 
canal Móvil (340 bytes) resultó más pesada que la de Web (280 bytes) para la cuenta 101, 
justo al revés de lo que esperaba, así que antes de dar por buena la evidencia 
fui a revisar por qué.

Al mirar el dataset me di cuenta de que la cuenta 101 es, de hecho, la que más movimientos 
tiene en todo cuentas_anuales.csv, y aun así son solo dos. El BFF Móvil, en cambio, siempre 
devuelve las mismas cinco "últimas transacciones", que salen de transacciones.csv, un archivo 
que registra actividad general del banco y no viene ligado a ninguna cuenta en particular. 
Por eso su tamaño no cambia según la cuenta que se consulte: no está optimizado de menos, 
es que compite contra un dataset de prueba demasiado pequeño como para que Web se note más pesado. 
Con datos reales, donde una cuenta acumula historial de verdad, esta relación se invertiría sin problema. 
Dejé el detalle completo en la sección anterior para que quede claro que es una limitación del dataset 
y no un error de diseño.

--- Reflexion tecnica 

Diseñar la separación entre 401 y 403 en el papel era fácil de justificar: el 401 
es para cuando el sistema no logra confirmar quién está llamando, y el 403 es para 
cuando ya sabe exactamente quién es, pero ese canal no tiene permiso sobre la ruta 
que está pidiendo. Aun así, hasta que no lo probé contra el servidor real, esa 
distinción era solo una idea razonable, no un hecho comprobado. Pedí /api/web/cuentas 
sin ningún token y me llegó un 401 con "Falta el header Authorization con el token Bearer". 
Después pedí lo mismo, pero con un token que había sacado para MOVIL, y ahí cambió a 403, 
con "El token pertenece al canal MOVIL y no tiene permiso para acceder al canal WEB". 
Ese cambio de código, y de mensaje, según cuál de los dos casos ocurra, es lo que me 
confirmó que la separación no se quedó solo en el diseño: el sistema efectivamente 
distingue entre no reconocer a alguien y reconocerlo pero no dejarlo pasar.

HTTPS lo di casi por descontado, y funcionó como esperaba: el servidor 
levantó en https://localhost:8443, y tuve que agregar el flag -k a cada 
curl porque el certificado es autofirmado. No hubo ninguna sorpresa ahí, 
pero de todas formas lo comprobé, porque desde S4 aprendí que dar algo 
por resuelto sin verlo correr es la forma más fácil de llevarse una sorpresa después.

--- Riesgos y recomendaciones

- El secreto usado para firmar los JWT (`jwt.secreto`) esta en
  `application.properties` en texto plano, igual que las llaves de canal
  en S4. Soporta bien el alcance que solicita esta actividad, pero en un entorno
  real iria en una variable de entorno o un gestor de secretos.
- El certificado HTTPS es autofirmado: esto sirve para demostrar el patron a
  nivel de curso, pero hay que tener claro que no es valido 
  para un dominio publico real ni para un navegador sin que 
  el usuario acepte la excepcion de seguridad.
- El token no tiene mecanismo de refresh: al vencer (15 minutos), el
  canal debe volver a pedir uno nuevo con `POST /api/auth/token`. Para
  esta actividad es aceptable porque no hay una sesion de usuario final
  detras, solo un canal tecnico.
- El saldo se sigue actualizando solo en memoria (decision heredada de
  S4): al reiniciar la aplicacion, cualquier retiro hecho se pierde y
  vuelve al valor original del CSV.

--- Repositorio en GitHub

https://github.com/Zersource/banco-xyz-bff
