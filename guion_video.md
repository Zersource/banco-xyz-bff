# Guion para video de presentacion — EFT Banco XYZ

> Duracion objetivo: 5-7 minutos
> Formato: grabacion de pantalla + voz en off (o camara + pantalla)
> Evaluacion: 10 pts (rubrica item 8)

---

## INTRO (0:00 – 0:30) — 30 segundos

**[Pantalla: portada con titulo del proyecto]**

> "Hola, soy Sergio Mascareno. En este video presento la Evaluacion Final Transversal del ramo Backend III: la migracion del sistema legacy del Banco XYZ a una arquitectura de microservicios con Spring Boot, Kafka y Docker."
>
> "El proyecto tiene 10 aplicaciones Java independientes, un broker Kafka y todo corre contenerizado con Docker Compose. Voy a cubrir cuatro puntos: un resumen ejecutivo, los resultados comparados con el sistema legacy, los desafios que enfrente con sus soluciones, y las propuestas de mejora."

---

## PARTE 1: RESUMEN EJECUTIVO (0:30 – 2:00) — 1 minuto 30 segundos

**[Pantalla: diagrama de arquitectura del README o del informe tecnico, pagina 4]**

> "La migracion se organizo en cinco ejes."

> "Primero, **Spring Batch**: converti los tres procesos legacy — transacciones diarias, intereses mensuales y estados de cuenta — a jobs de Spring Batch con Reader, Processor y Writer. Cada job valida los registros, hace skip de los invalidos, retry ante fallos transitorios, y si el contenedor falla, Docker lo reinicia automaticamente hasta 3 veces."

**[Pantalla: tabla de los 3 jobs del informe, pagina 5]**

> "Segundo, **el patron BFF**: implemente tres Backend for Frontend diferenciados. El BFF web entrega datos completos — cuentas, transacciones y transferencias. El BFF movil devuelve un payload liviano con solo id, saldo y titular. Y el BFF cajero expone unicamente saldo y retiro. Cada uno tiene su propio scope OAuth2 y Circuit Breaker."

**[Pantalla: tabla BFF del informe, pagina 6]**

> "Tercero, **microservicios con Kafka**: los tres servicios de negocio — cuentas, clientes y pagos — se comunican por Kafka con una saga coreografiada de transferencias. Si el debito funciona pero el credito falla, cuentas compensa automaticamente devolviendo el monto."

> "Cuarto, **seguridad OAuth2**: use Spring Authorization Server con client_credentials, JWT firmado con RSA y JWKS publico. Cada canal tiene su propio cliente y scope, y un token de un canal no puede acceder a otro."

> "Y quinto, **Docker**: todo esta contenerizado con builds multi-stage, healthchecks, arranque ordenado con depends_on y escalabilidad horizontal demostrada con 2 replicas de bff-web detras de nginx."

---

## PARTE 2: RESULTADOS VS SISTEMA LEGACY (2:00 – 3:30) — 1 minuto 30 segundos

**[Pantalla: terminal con `docker compose ps` mostrando 10 servicios healthy]**

> "Antes, el Banco XYZ tenia un sistema monolitico con procesos batch basados en archivos planos, sin separacion de canales, sin tolerancia a fallos y sin comunicacion asincrona."

> "Ahora tenemos 10 servicios independientes corriendo en Docker. Veamos los resultados concretos."

**[Pantalla: ejecucion del e2e mostrando 95/95]**

> "La prueba end-to-end automatizada ejecuta 95 verificaciones: seguridad OAuth2 por canal — tokens, scopes, 401, 403 — endpoints de los tres BFF, saga de transferencias con exito y compensacion, reinicio de pagos con UUID, Circuit Breaker con las transiciones CLOSED, OPEN, HALF_OPEN y vuelta a CLOSED, y peso de payloads por canal. Las 95 pasan."

**[Pantalla: ejecucion del batch mostrando 3 jobs EXITO]**

> "Los tres jobs batch procesan los datos legacy: dailyTransactionsJob cargo 387 transacciones filtrando 599 anomalias, monthlyInterestJob calculo 50 registros de interes, y annualStatementJob genero 614 estados de cuenta. Los tres usan particionamiento con 3 workers y terminan con EXITO."

**[Pantalla: evidencia de escala — 2 instancias en Eureka, balanceo 5/5]**

> "Y la escalabilidad horizontal funciona: 2 replicas de bff-web detras de nginx, Eureka muestra ambas instancias, y el balanceo distribuye 5 y 5 las peticiones. Ambas replicas validan el mismo token OAuth2. Todo esto se ejecuto en un clon limpio del repositorio, sin dependencias locales."

---

## PARTE 3: DESAFIOS Y SOLUCIONES (3:30 – 5:00) — 1 minuto 30 segundos

**[Pantalla: informe tecnico, seccion 10]**

> "Enfrente cuatro desafios principales durante la implementacion."

> "El primero fue el **token invalido tras reinicio del auth-server**. Al reiniciar, el auth-server genera un nuevo par de claves RSA, pero los tokens en cache de los BFF siguen firmados con la clave anterior. Cuando cuentas descarga el nuevo JWKS, rechaza el token viejo y produce un 401 transitorio que dura hasta que el token vence — maximo 15 minutos. Documente esto como hallazgo y lo reproduje de forma determinista. La solucion definitiva seria persistir la clave RSA, pero esta fuera del alcance."

> "El segundo fue el **orden de arranque**. Originalmente usaba scripts bash con sleep y curl. Lo reemplace por depends_on con service_healthy, que es la forma nativa de Compose. El costo fue exponer /actuator/health sin token en los microservicios backend."

> "El tercero fue el **escalado con replicas**. Usar --scale en la linea de comandos no persistia: un docker compose up -d posterior volvia a 1 replica. La solucion fue declarar deploy.replicas: 2 en un override YAML con !override para reasignar puertos."

> "Y el cuarto fue el **balanceo de nginx**. Distribuia 10/0 porque tenia 12 workers y sin la directiva zone el upstream no compartia estado. Al agregar zone, la distribucion paso a 5/5."

---

## PARTE 4: PROPUESTAS DE MEJORA (5:00 – 6:00) — 1 minuto

**[Pantalla: informe tecnico, seccion 11]**

> "Para llevar este ecosistema a produccion propongo seis mejoras."

> "Primero, reemplazar **H2 en memoria por PostgreSQL** en cuentas y pagos, usando RDS en AWS. Esto permite que los saldos sobrevivan reinicios y escalar esos servicios horizontalmente."

> "Segundo, mover los **secretos** OAuth2 y de Kafka a AWS Secrets Manager o Vault, sacandolos del config-repo."

> "Tercero, **persistir la clave RSA** del auth-server en un volumen o en Secrets Manager para resolver el problema del token invalido tras reinicio."

> "Cuarto, colocar un **Application Load Balancer con HTTPS** delante de los BFF, terminando TLS en el balanceador con certificado ACM."

> "Quinto, reemplazar el **Kafka standalone por Amazon MSK** para alta disponibilidad y replicacion."

> "Y sexto, migrar de Docker Compose a **ECS Fargate o EKS** para auto-scaling, rolling updates y service mesh."

---

## CIERRE (6:00 – 6:30) — 30 segundos

**[Pantalla: README del proyecto o portada del informe]**

> "En resumen, el proyecto demuestra una migracion completa del sistema legacy a microservicios. Los cinco ejes se implementaron y verificaron con 95 pruebas automatizadas, ejecutadas en un clon limpio del repositorio. Todo el codigo esta en GitHub en la rama eft-final. Gracias."

---

## Notas para la grabacion

1. **Duracion total estimada: ~6 minutos 30 segundos** (dentro del rango 5-7 min)
2. **Pantallas sugeridas para compartir:**
   - Portada del informe PDF
   - Diagrama de arquitectura (README o informe)
   - Tablas del informe (batch, BFF, topics Kafka, Circuit Breaker)
   - Terminal: `docker compose ps` (10 healthy)
   - Terminal: `prueba-e2e.sh` (95/95 OK)
   - Terminal: batch (3 jobs EXITO)
   - Terminal: escala (2 replicas, Eureka, balanceo 5/5)
   - Secciones 10 y 11 del informe
3. **Tono:** profesional pero natural, primera persona, como si presentaras a un comite tecnico
4. **Tip:** graba la pantalla primero sin audio, luego graba la voz encima. Asi puedes repetir partes sin rehacer toda la grabacion
5. **Software sugerido:** OBS Studio (gratis) o QuickTime (macOS) para captura de pantalla + audio
