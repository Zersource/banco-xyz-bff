# Propuesta Tecnica - Exp3 S6: Microservicios y seguridad en la nube con Spring Cloud

--- Contexto ---

  Esta semana de exp3 en S6, la actividad pide avanzar en el proyecto
en su continuidad implementando microservicios con Spring Cloud: Config Server,
Service Discovery con Eureka, y tolerancia a fallos con Circuit Breaker, ademas de
mantener un sistema de autenticacion. Como la instruccion dice explicitamente que
es continuidad y no un proyecto nuevo, parti del BFF de Exp2 S5 (el que ya tenia
JWT, autorizacion por canal y HTTPS), no de un ejemplo hecho desde cero.

--- Que cambia respecto a la semana pasada de S5 ---

--- De monolito con 3 fachadas a 5 microservicios reales ---

  El BFF de S5 era un solo proceso Spring Boot con tres paquetes de controllers
(bff.web, bff.movil, bff.cajero) que le pegaban directo al mismo CuentaService en
memoria. Eso servia para mostrar el patron BFF, pero no era realmente un
ecosistema de microservicios: no habia ninguna llamada por red entre servicios,
asi que tampoco habia nada de verdad que un Circuit Breaker pudiera proteger.

  Separe el proyecto en 5 modulos, cada uno con su propio pom.xml y su propio
proceso:

- `eureka-server` (8761): Service Discovery. No consume su propia configuracion
  desde config-server a proposito. Si dependiera de el, y config-server a su vez
  se descubriera via Eureka, quedaria una dependencia circular al arrancar.
- `config-server` (8888): configuracion centralizada. Use el backend native
  (una carpeta config-repo/ dentro del mismo modulo) en vez de un repositorio
  Git o S3 externo como sugiere la guia, para no meter un repositorio adicional
  fuera del alcance de la actividad. Para los microservicios que lo consumen el
  resultado es identico.
- `bff-web` (8081): paso a ser el unico dueno de los datos, el CSV en memoria
  que antes compartian los tres. Ademas de su propio canal, ahora expone
  /interno/** (InternoController) para que bff-movil y bff-cajero le pidan la
  informacion que necesitan.
- `bff-movil` (8082) y `bff-cajero` (8083): ya no cargan datos propios. Un
  cliente nuevo, BffWebClient (un RestTemplate con @LoadBalanced), le pide todo
  a bff-web por nombre de servicio via Eureka (http://bff-web/...), no por un
  host:puerto fijo. Cada llamada esta envuelta con @CircuitBreaker de
  Resilience4j y su fallback correspondiente.

  Antes de decidirme por esta forma, pense en una variante mas simple: que los
tres BFF se llamaran entre si en vez de separar los datos en bff-web. Pero como
bff-web ya era el que tenia el dataset completo desde S4, hacerlo dueno de los
datos aprovecha lo que ya existia en vez de inventar un servicio nuevo, que era
justo el criterio que use para no salirme del nivel de la materia.

--- Autenticacion: se mantiene, no se rehace ---

  El JWT de S5 sigue funcionando exactamente igual: bff-web es el unico que
emite tokens (POST /api/auth/token, sin ningun cambio), y ahora bff-movil y
bff-cajero validan ese mismo token cada uno por su cuenta, con una copia de
JwtService (solo para leer, no para emitir) y la misma llave HMAC (jwt.secreto),
compartida a los tres servicios via config-server. No hay ninguna llamada de red
para validar un token: cada microservicio lo verifica localmente contra la
firma.

--- HTTPS: se retira esta semana (decision de alcance) ---

  Le saque el HTTPS con certificado autofirmado que habia armado en S5. Meter
TLS entre tres servicios que se descubren via Eureka significa manejar un trust
store en cada cliente para que confien en el certificado autofirmado de
bff-web, y eso es un problema de mTLS que la guia de esta semana no toca en
ningun momento. El foco declarado es Config Server, Eureka y Circuit Breaker,
nada mas. Lo dejo anotado aqui como una decision de alcance a proposito, igual
que las excepciones que ya tengo registradas para Spring Batch: si una semana
futura pide retomarlo, se puede.

--- Como quedo la estructura del proyecto (actualizada) ---

```
banco-xyz-bff/
 |- eureka-server/    (EurekaServerApplication)
 |- config-server/    (ConfigServerApplication, config-repo/ con native backend)
 |- bff-web/
 |    |- model, repository, service     (dueno de los datos, sin cambios de S5)
 |    |- auth                           (JwtService, TokenController - sin cambios)
 |    |- bff.web                        (controller + dto - sin cambios)
 |    |- interno                        (InternoController)                    <- nuevo
 |    |- config, exception
 |- bff-movil/
 |    |- client         (BffWebClient: RestTemplate @LoadBalanced + CircuitBreaker) <- nuevo
 |    |- dto            (CuentaDTO/TransaccionDTO: espejo de los modelos de bff-web) <- nuevo
 |    |- auth, config, exception        (copias locales de validacion JWT)
 |    |- bff.movil, service
 |- bff-cajero/         (misma forma que bff-movil, para saldo/retiro)
```

--- Problemas encontrados y como los resolvi ---

  A diferencia de S4 y S5, esta vez mvn clean compile paso limpio a la primera
en los cinco modulos. Los problemas de verdad aparecieron recien al correr el
ecosistema completo y probar la tolerancia a fallos de verdad, no en la
compilacion.

  El primero fue que el Circuit Breaker no interceptaba nada. La primera vez
que probe matar bff-web y repetir la llamada a bff-movil, en vez del 503 que
esperaba me llego un 500 con el stack trace crudo de
ResourceAccessException: Connection refused. La causa: yo habia agregado
spring-cloud-starter-circuitbreaker-resilience4j al pom.xml, pero esa
dependencia solo expone CircuitBreakerFactory, para uso programatico, y no
trae el aspecto AOP que procesa la anotacion @CircuitBreaker que use en
BffWebClient. Sin ese aspecto la anotacion queda de adorno, y la excepcion de
RestTemplate se propaga entera sin pasar por el fallback. Agregue
io.github.resilience4j:resilience4j-spring-boot3, con su BOM para fijar la
version en linea con lo que ya traia el starter de Spring Cloud. Compilaba
bien, pero el circuito seguia sin cortar.

  Faltaba una segunda pieza. resilience4j-spring-boot3 aporta la clase del
aspecto, CircuitBreakerAspect, pero sin aspectjweaver en el classpath Spring no
lo registra como interceptor. Agregando spring-boot-starter-aop, que trae
aspectjweaver, recien ahi el circuito empezo a cortar de verdad: mate bff-web,
repeti la llamada cuatro veces seguidas, y las cuatro respondieron en unos 14
ms cada una con el 503 esperado. Ese tiempo tan bajo es justamente lo que
confirma que el aspecto esta interceptando antes de siquiera intentar la
conexion, no esperando un timeout largo.

  El segundo problema fue que un 404 real se me convertia en 503. Al confirmar
que el circuito cortaba bien, probe con una cuenta que no existe (id 1), y
bff-movil me devolvio 503 en vez del 404 real que entrega bff-web. Peor
todavia: tres consultas seguidas a esa cuenta abrian el circuito, asi que
consultas validas despues tambien recibian 503 por los siguientes 10 segundos.
El fallback de BffWebClient capturaba cualquier Throwable sin distinguir un
4xx real, que es una respuesta valida de negocio, de una caida real del
servicio. Agregue ignore-exceptions: HttpClientErrorException a la config del
circuito en config-server, pero eso solo evita que ese error cuente para abrir
el circuito, no evita que el aspecto igual invoque el fallbackMethod para esa
misma excepcion. La cuenta 1 seguia dando 503, aunque al menos el circuito ya
no se abria por su culpa. La solucion final fue distinguir dentro del propio
fallback: si el error es un HttpClientErrorException se relanza tal cual, para
que lo capture un @ExceptionHandler nuevo que reenvia el status y el cuerpo
reales de bff-web; cualquier otra causa, como timeout, conexion rechazada o
circuito abierto, si se convierte en ServicioNoDisponibleException, o sea en
503. Con ese cambio la cuenta inexistente volvio a dar 404 real, y el circuito
dejo de abrirse por errores de negocio.

--- Reflexion tecnica ---

  Los dos problemas del Circuit Breaker tienen algo en comun que recien me
quedo claro al verlos en la practica: la anotacion @CircuitBreaker no es una
sola pieza, son tres (la libreria base, el aspecto AOP que la procesa, y
aspectjweaver para que Spring registre ese aspecto), y si falta cualquiera de
las tres el codigo compila perfecto pero se comporta como si la anotacion ni
existiera, sin ningun error visible hasta que se prueba el escenario de falla
real. Ahi confirme otra vez algo que ya habia anotado en S5: dar algo por
resuelto solo porque compila, sin verlo correr contra el escenario que de
verdad le importa a la pauta, es la forma mas facil de llevarse una sorpresa.

  Lo del 404 fue distinto: no era una pieza faltante, era una suposicion mia
que resulto incorrecta sobre lo que hace ignore-exceptions. Asumi que
"ignorar" una excepcion en la config del circuito tambien significaba que el
fallback la iba a ignorar, y no es asi. Son dos preguntas separadas, si esto
cuenta como fallo para abrir el circuito y si esto deberia ir al fallback, que
yo estaba tratando como si fueran una sola. Tuve que mirar el comportamiento
real, ver que el 404 seguia convirtiendose en 503 pese a la config, para
darme cuenta de que eran cosas distintas.

--- Riesgos y recomendaciones ---

- jwt.secreto sigue en texto plano, ahora centralizado en config-server en vez
  de repetido en cada application.properties como en S5. Es mas ordenado, pero
  sigue sin ser un manejo de secretos real (variable de entorno o vault), lo
  cual queda fuera del alcance de esta actividad.
- El backend de config-server es native, es decir una carpeta local, no un
  repositorio Git externo. Es valido para este alcance, pero en un entorno
  real se perderia la trazabilidad de cambios de configuracion que da
  versionar config-repo/ aparte.
- Se retiro el HTTPS de S5 esta semana, como explique en la seccion de diseño.
  Si una semana futura pide seguridad de transporte entre microservicios, hay
  que resolver el problema de confianza del certificado autofirmado entre
  clientes, no basta con volver a activar server.ssl.*.
- Eureka corre como una sola instancia, sin peers: en desarrollo, con pocas
  instancias registradas, el dashboard muestra el warning de
  auto-preservacion ("EMERGENCY! ... RENEWALS ARE LESSER THAN THRESHOLD"). Es
  el comportamiento esperado de Eureka con pocos nodos, no un error real.
- El saldo se sigue actualizando solo en memoria, una decision heredada desde
  S4: al reiniciar bff-web, cualquier retiro hecho se pierde y vuelve al valor
  original del CSV.
- El Circuit Breaker distingue un 4xx de una caida real solo en las dos
  llamadas que hace BffWebClient hoy (cuenta, y transacciones o saldo o
  retiro). Si mas adelante se agregan nuevas llamadas a bff-web, hay que
  repetir el mismo patron a mano dentro de cada fallback
  (if (error instanceof HttpClientErrorException) throw error;), no es algo
  que quede resuelto solo por la configuracion.

--- Repositorio en GitHub ---

https://github.com/Zersource/banco-xyz-bff
