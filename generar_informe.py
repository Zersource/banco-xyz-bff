#!/usr/bin/env python3
"""Genera el informe tecnico PDF de la EFT — Banco XYZ."""

from reportlab.lib.pagesizes import letter
from reportlab.lib.units import cm, mm
from reportlab.lib.colors import HexColor, black, white
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.enums import TA_LEFT, TA_CENTER, TA_JUSTIFY
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle,
    KeepTogether, HRFlowable,
)
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
import os

# ---------- colores y fuentes ----------
NARANJO = HexColor("#C55A11")
GRIS_FONDO = HexColor("#F2F2F2")
GRIS_BORDE = HexColor("#CCCCCC")
NEGRO = black
BLANCO = white

# Helvetica (built-in, similar a Arial) en vez de registrar fuente externa
FONT = "Helvetica"
FONT_B = "Helvetica-Bold"
FONT_I = "Helvetica-Oblique"

# ---------- estilos ----------
styles = getSampleStyleSheet()

s_portada_titulo = ParagraphStyle(
    "PortadaTitulo", fontName=FONT_B, fontSize=22, leading=26,
    textColor=NARANJO, alignment=TA_CENTER, spaceAfter=6,
)
s_portada_sub = ParagraphStyle(
    "PortadaSub", fontName=FONT, fontSize=14, leading=18,
    textColor=NEGRO, alignment=TA_CENTER, spaceAfter=4,
)
s_portada_dato = ParagraphStyle(
    "PortadaDato", fontName=FONT, fontSize=11, leading=15,
    textColor=NEGRO, alignment=TA_CENTER, spaceAfter=2,
)
s_h1 = ParagraphStyle(
    "H1", fontName=FONT_B, fontSize=16, leading=20,
    textColor=NARANJO, spaceBefore=18, spaceAfter=10,
)
s_h2 = ParagraphStyle(
    "H2", fontName=FONT_B, fontSize=13, leading=16,
    textColor=NARANJO, spaceBefore=14, spaceAfter=8,
)
s_h3 = ParagraphStyle(
    "H3", fontName=FONT_B, fontSize=11, leading=14,
    textColor=NEGRO, spaceBefore=10, spaceAfter=6,
)
s_body = ParagraphStyle(
    "Body", fontName=FONT, fontSize=10, leading=14,
    textColor=NEGRO, alignment=TA_JUSTIFY, spaceAfter=6,
)
s_body_indent = ParagraphStyle(
    "BodyIndent", parent=s_body, leftIndent=18,
)
s_code = ParagraphStyle(
    "Code", fontName="Courier", fontSize=8.5, leading=11,
    textColor=NEGRO, backColor=GRIS_FONDO, borderWidth=0.5,
    borderColor=GRIS_BORDE, borderPadding=6, spaceAfter=8,
    leftIndent=12, rightIndent=12,
)
s_caption = ParagraphStyle(
    "Caption", fontName=FONT_I, fontSize=9, leading=12,
    textColor=NEGRO, alignment=TA_CENTER, spaceBefore=4, spaceAfter=10,
)
s_bullet = ParagraphStyle(
    "Bullet", fontName=FONT, fontSize=10, leading=14,
    textColor=NEGRO, leftIndent=24, bulletIndent=12, spaceAfter=3,
)


def b(text):
    return f"<b>{text}</b>"


def i(text):
    return f"<i>{text}</i>"


def _cell(text, bold=False):
    """Wraps text in a Paragraph so it word-wraps inside table cells."""
    font = FONT_B if bold else FONT
    return Paragraph(text, ParagraphStyle("cell", fontName=font, fontSize=9, leading=12))


def make_table(headers, rows, col_widths=None, wrap_cols=None):
    """Crea una tabla con estilo uniforme.
    wrap_cols: set of column indices whose cells should word-wrap via Paragraph.
    """
    if wrap_cols:
        headers = [_cell(h, bold=True) if i in wrap_cols else h for i, h in enumerate(headers)]
        new_rows = []
        for row in rows:
            new_rows.append([_cell(c) if i in wrap_cols else c for i, c in enumerate(row)])
        rows = new_rows
    data = [headers] + rows
    t = Table(data, colWidths=col_widths, repeatRows=1)
    t.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), NARANJO),
        ("TEXTCOLOR", (0, 0), (-1, 0), BLANCO),
        ("FONTNAME", (0, 0), (-1, 0), FONT_B),
        ("FONTSIZE", (0, 0), (-1, 0), 9),
        ("FONTNAME", (0, 1), (-1, -1), FONT),
        ("FONTSIZE", (0, 1), (-1, -1), 9),
        ("LEADING", (0, 0), (-1, -1), 12),
        ("ALIGN", (0, 0), (-1, -1), "LEFT"),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("GRID", (0, 0), (-1, -1), 0.5, GRIS_BORDE),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [BLANCO, GRIS_FONDO]),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
    ]))
    return t


# ---------- contenido ----------
story = []

# ===== PORTADA =====
story.append(Spacer(1, 6 * cm))
story.append(Paragraph("Informe Tecnico", s_portada_titulo))
story.append(Paragraph("Evaluacion Final Transversal (EFT)", s_portada_sub))
story.append(Spacer(1, 0.8 * cm))
story.append(Paragraph("Migracion del sistema legacy del Banco XYZ<br/>a microservicios con Spring Cloud, Kafka y Docker", s_portada_sub))
story.append(Spacer(1, 1.5 * cm))
story.append(HRFlowable(width="60%", thickness=1, color=NARANJO, spaceAfter=12))
story.append(Paragraph("PBY2203 — Desarrollo Backend III", s_portada_dato))
story.append(Paragraph("DuocUC Online", s_portada_dato))
story.append(Spacer(1, 0.5 * cm))
story.append(Paragraph("Sergio Mascareno", s_portada_dato))
story.append(Paragraph("Octubre 2026", s_portada_dato))
story.append(Spacer(1, 1 * cm))
story.append(Paragraph("Repositorio: https://github.com/Zersource/banco-xyz-bff", s_portada_dato))
story.append(Paragraph("Rama: eft-final", s_portada_dato))
story.append(PageBreak())

# ===== INDICE =====
story.append(Paragraph("Indice", s_h1))
indice_items = [
    "1. Resumen ejecutivo",
    "2. Contexto y requerimientos de negocio",
    "3. Arquitectura propuesta",
    "4. Procesos batch (Spring Batch)",
    "5. Patron BFF por canal",
    "6. Microservicios, Kafka y resiliencia",
    "7. Seguridad con Spring Cloud Security (OAuth2)",
    "8. Contenerizacion y escalabilidad con Docker",
    "9. Evidencia de ejecucion",
    "10. Desafios y soluciones",
    "11. Propuestas de mejora",
    "12. Conclusiones",
]
for item in indice_items:
    story.append(Paragraph(item, s_body))
story.append(PageBreak())

# ===== 1. RESUMEN EJECUTIVO =====
story.append(Paragraph("1. Resumen ejecutivo", s_h1))
story.append(Paragraph(
    "Este informe documenta la migracion del sistema legacy del Banco XYZ a una arquitectura "
    "de microservicios. El proyecto abarca cinco ejes de migracion: la conversion de procesos "
    "batch a Spring Batch con manejo de errores y paralelismo; la separacion del monolito en "
    "microservicios independientes con Spring Cloud (Eureka, Config Server); la implementacion "
    "del patron BFF para tres canales (web, movil, cajero); la seguridad con OAuth2 usando "
    "Spring Authorization Server (client_credentials, JWT, JWKS); y la mensajeria asincrona "
    "con Apache Kafka para una saga de transferencias con compensacion.", s_body))
story.append(Paragraph(
    "Todo el ecosistema — 10 aplicaciones Java, un broker Kafka y un balanceador nginx — se "
    "contenerizo con Docker y Docker Compose, con builds multi-stage, healthchecks, arranque "
    "ordenado y escalabilidad horizontal demostrada en los BFF. El stack completo se verifico "
    "con una prueba end-to-end automatizada de 95 verificaciones, ejecutada tanto en local "
    "como en Docker, incluyendo un clon limpio del repositorio.", s_body))

# ===== 2. CONTEXTO =====
story.append(Paragraph("2. Contexto y requerimientos de negocio", s_h1))
story.append(Paragraph(
    "El Banco XYZ opera un sistema legacy monolitico con procesos batch basados en archivos "
    "planos, sin separacion de canales y sin tolerancia a fallos. La gerencia de TI requiere "
    "migrar a una arquitectura moderna que cumpla tres requerimientos de negocio:", s_body))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Continuidad operacional:") + " los procesos batch de transacciones diarias, "
    "intereses mensuales y estados de cuenta anuales deben seguir funcionando sin perdida de datos, "
    "con capacidad de recuperacion ante fallos transitorios y permanentes.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Experiencia diferenciada por canal:") + " cada canal de atencion (web, "
    "movil, cajero) necesita una fachada optimizada que entregue solo los datos pertinentes, con "
    "seguridad independiente y tolerancia a fallos cuando un servicio backend no este disponible.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Transferencias en tiempo real:") + " el sistema de transferencias entre "
    "cuentas debe ser asincrono, garantizar consistencia eventual (debito-credito atomico) y compensar "
    "automaticamente ante fallos, notificando a los titulares.", s_bullet))
story.append(Paragraph(
    "Estos tres requerimientos justifican la arquitectura propuesta: Spring Batch para el procesamiento "
    "legacy, BFF con Circuit Breaker para los canales, y una saga coreografiada sobre Kafka para las "
    "transferencias.", s_body))

# ===== 3. ARQUITECTURA =====
story.append(Paragraph("3. Arquitectura propuesta", s_h1))
story.append(Paragraph(
    "Disene la arquitectura como un ecosistema de 10 aplicaciones Spring Boot independientes, cada "
    "una con su propio modulo Maven, su Dockerfile y su configuracion en el Config Server. No use "
    "un POM padre: cada modulo se compila y despliega de forma autonoma.", s_body))

story.append(Paragraph("3.1 Componentes de infraestructura", s_h2))
story.append(make_table(
    ["Componente", "Puerto", "Tecnologia", "Funcion"],
    [
        ["config-server", "8888", "Spring Cloud Config (native)", "Configuracion centralizada para todos los servicios"],
        ["eureka-server", "8761", "Spring Cloud Netflix Eureka", "Registro y descubrimiento de servicios"],
        ["auth-server", "9000", "Spring Authorization Server 1.3.2", "Emision de tokens OAuth2, JWKS publico"],
        ["kafka", "9094", "Apache Kafka 3.7 (KRaft)", "Broker de mensajeria para la saga"],
    ],
    col_widths=[3*cm, 1.8*cm, 5*cm, 7*cm],
    wrap_cols={2, 3},
))
story.append(Spacer(1, 4))

story.append(Paragraph("3.2 Microservicios de negocio", s_h2))
story.append(make_table(
    ["Servicio", "Puerto", "Responsabilidad"],
    [
        ["cuentas", "8084", "Cuentas, saldos, movimientos. Ejecuta debito, credito y compensacion de la saga"],
        ["clientes", "8085", "Datos del titular de cada cuenta. Notifica transferencias completadas"],
        ["pagos", "8086", "Recibe transferencias, las persiste en H2 y orquesta la saga via Kafka"],
    ],
    col_widths=[2.5*cm, 1.8*cm, 12.5*cm],
    wrap_cols={2},
))
story.append(Spacer(1, 4))

story.append(Paragraph("3.3 BFF (Backend for Frontend)", s_h2))
story.append(make_table(
    ["BFF", "Puerto", "Canal", "Datos expuestos"],
    [
        ["bff-web", "8081", "web", "Cuentas completas, transacciones, transferencias"],
        ["bff-movil", "8082", "movil", "Cuenta resumida (payload liviano)"],
        ["bff-cajero", "8083", "cajero", "Solo saldo y retiro"],
    ],
    col_widths=[2.5*cm, 1.8*cm, 2*cm, 10.5*cm],
))

story.append(Paragraph("3.4 Diagrama de flujo", s_h2))
story.append(Paragraph(
    "cliente &rarr; token &rarr; bff-web / bff-movil / bff-cajero<br/>"
    "&nbsp;&nbsp;&nbsp;&nbsp;&darr; (Eureka + RestClient + Circuit Breaker)<br/>"
    "cuentas &nbsp;&nbsp; clientes &nbsp;&nbsp; pagos<br/>"
    "&nbsp;&nbsp;&nbsp;&nbsp;&darr;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"
    "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&darr;<br/>"
    "&nbsp;&nbsp;&nbsp;&nbsp;Kafka (saga de transferencias)", s_code))
story.append(Paragraph(
    "Todos los servicios se registran en Eureka y obtienen su configuracion del Config Server. "
    "Los BFF resuelven los servicios por nombre (no por host:puerto fijo) y protegen cada llamada "
    "con Circuit Breaker. Los microservicios de negocio se comunican entre si exclusivamente por Kafka.", s_body))

story.append(PageBreak())

# ===== 4. BATCH =====
story.append(Paragraph("4. Procesos batch (Spring Batch)", s_h1))
story.append(Paragraph(
    "Implemente tres jobs de Spring Batch que procesan los datos legacy del banco, cada uno con su "
    "propio Reader, Processor y Writer, validacion de registros, manejo de skip para datos invalidos "
    "y retry ante fallos transitorios.", s_body))

story.append(Paragraph("4.1 Jobs implementados", s_h2))
story.append(make_table(
    ["Job", "Descripcion", "Resultado"],
    [
        ["dailyTransactionsJob", "Carga transacciones diarias, filtra anomalias",
         "387 escritos, 599 anomalias"],
        ["monthlyInterestJob", "Calcula intereses mensuales sobre saldos",
         "50 escritos, 601 anomalias"],
        ["annualStatementJob", "Genera estados de cuenta anuales",
         "614 escritos, 538 anomalias"],
    ],
    col_widths=[4*cm, 8.3*cm, 4.5*cm],
))

story.append(Paragraph("4.2 Manejo de errores", s_h2))
story.append(Paragraph(
    "Configure tres niveles de proteccion ante fallos:", s_body))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Skip:") + " los registros que no pasan la validacion de negocio "
    "(formato invalido, campos faltantes, montos negativos) se omiten y se registran en el SkipListener "
    "para auditoria. El job continua sin detenerse.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Retry:") + " ante fallos transitorios (TransientDataAccessException), "
    "el step reintenta hasta 3 veces antes de fallar. Esto cubre interrupciones momentaneas de I/O.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Restart con Docker:") + " el contenedor batch tiene restart_policy "
    "con max_attempts: 3. Si el job falla (exit code 1), Docker lo reinicia automaticamente. "
    "Probado con falla permanente (4 intentos, exit 1, RestartCount 3) y falla transitoria "
    "(el CSV aparece en el segundo intento, exit 0, RestartCount 1).", s_bullet))

story.append(Paragraph("4.3 Paralelismo y escalabilidad", s_h2))
story.append(Paragraph(
    "El dailyTransactionsJob usa particionamiento con 3 workers: el Partitioner divide el archivo "
    "de entrada en 3 segmentos y cada worker (partition0, partition1, partition2) procesa su rango "
    "de forma concurrente con un TaskExecutor de pool size 3. Los otros dos jobs procesan "
    "secuencialmente porque su logica de negocio (interes acumulado, estado de cuenta consolidado) "
    "requiere acceso al dataset completo.", s_body))

story.append(PageBreak())

# ===== 5. BFF =====
story.append(Paragraph("5. Patron BFF por canal", s_h1))
story.append(Paragraph(
    "Implemente el patron Backend for Frontend con tres fachadas independientes, cada una optimizada "
    "para su canal de atencion. La diferencia entre canales no es solo de seguridad (scope OAuth2) sino "
    "de contenido: cada BFF expone endpoints distintos con payloads de peso diferente.", s_body))

story.append(Paragraph("5.1 Diferenciacion por canal", s_h2))
story.append(make_table(
    ["Canal", "BFF", "Endpoints", "Caracteristicas"],
    [
        ["Web", "bff-web (8081)", "/api/web/cuentas, /api/web/transacciones, /transferencias",
         "Datos completos: saldo, titular, movimientos, transacciones. Unico canal con transferencias"],
        ["Movil", "bff-movil (8082)", "/api/movil/cuentas/{id}",
         "Respuesta liviana: solo id, saldo y titular. Payload reducido para conexiones moviles"],
        ["Cajero", "bff-cajero (8083)", "/api/cajero/cuentas/{id}/saldo, .../retiro",
         "Operaciones basicas: consulta de saldo y retiro. Sin acceso a transacciones ni transferencias"],
    ],
    col_widths=[1.8*cm, 3*cm, 5*cm, 7*cm],
    wrap_cols={2, 3},
))

story.append(Paragraph("5.2 Rendimiento por canal", s_h2))
story.append(Paragraph(
    "El script e2e verifica que el peso del payload es coherente con el proposito de cada canal. "
    "El BFF web devuelve el dataset completo (mayor peso), el movil un resumen ligero y el cajero "
    "solo el saldo numerico (menor peso). Esta verificacion se ejecuta automaticamente en las "
    "95 pruebas del e2e.", s_body))

story.append(Paragraph("5.3 Seguridad por canal", s_h2))
story.append(Paragraph(
    "Cada BFF exige un scope OAuth2 especifico. Un token emitido para el canal web (scope=web) no "
    "puede acceder al BFF movil (scope=movil), y viceversa. El auth-server registra tres clientes "
    "independientes, uno por canal, cada uno con su propio secreto. La validacion del token se hace "
    "contra el JWKS publico del auth-server, sin llamadas de red adicionales.", s_body))

story.append(Paragraph("5.4 Circuit Breaker (Resilience4j)", s_h2))
story.append(Paragraph(
    "Cada BFF protege sus llamadas a cuentas y clientes con un Circuit Breaker de Resilience4j. "
    "Configure los parametros en el Config Server (config-repo):", s_body))
story.append(make_table(
    ["Parametro", "Valor", "Efecto"],
    [
        ["slidingWindowSize", "3", "Evalua los ultimos 3 llamados"],
        ["failureRateThreshold", "50", "Abre el circuito si >= 50% fallan"],
        ["waitDurationInOpenState", "10s", "Tiempo en OPEN antes de pasar a HALF_OPEN"],
        ["permittedNumberOfCalls InHalfOpenState", "2", "Llamados de prueba en HALF_OPEN"],
    ],
    col_widths=[5.5*cm, 2*cm, 9.3*cm],
    wrap_cols={0, 2},
))
story.append(Paragraph(
    "El fallback responde con HTTP 503 y un mensaje indicando que el servicio no esta disponible. "
    "Clientes es dato accesorio: si cae, web y movil responden igual pero sin el nombre del titular. "
    "Las transiciones del breaker (CLOSED &rarr; OPEN &rarr; HALF_OPEN &rarr; CLOSED) se verificaron "
    "en Docker con logs de nivel DEBUG.", s_body))

story.append(PageBreak())

# ===== 6. MICROSERVICIOS + KAFKA =====
story.append(Paragraph("6. Microservicios, Kafka y resiliencia", s_h1))

story.append(Paragraph("6.1 Saga de transferencias", s_h2))
story.append(Paragraph(
    "Implemente una saga coreografiada sobre Kafka para las transferencias entre cuentas. Cada servicio "
    "reacciona a eventos sin un orquestador central: pagos inicia, cuentas ejecuta el debito y el credito, "
    "y si algo falla, cuentas compensa devolviendo el debito.", s_body))

story.append(Paragraph("Flujo exitoso:", s_h3))
story.append(Paragraph(
    "1. bff-web &rarr; POST /transferencias &rarr; pagos guarda como PENDIENTE<br/>"
    "2. pagos &rarr; transferencia.iniciada &rarr; cuentas debita la cuenta origen<br/>"
    "3. cuentas &rarr; transferencia.debito-realizado &rarr; cuentas acredita la cuenta destino<br/>"
    "4. cuentas &rarr; transferencia.completada &rarr; pagos marca COMPLETADA, clientes notifica", s_body_indent))

story.append(Paragraph("Flujo con compensacion (cuenta destino inexistente):", s_h3))
story.append(Paragraph(
    "1-3. Igual que arriba hasta el intento de credito<br/>"
    "4. Credito falla &rarr; cuentas publica transferencia.credito-fallido<br/>"
    "5. cuentas consume credito-fallido &rarr; devuelve el debito &rarr; transferencia.revertida<br/>"
    "6. pagos marca REVERTIDA", s_body_indent))

story.append(Paragraph("6.2 Topics de Kafka", s_h2))
story.append(make_table(
    ["Topic", "Publica", "Consume", "Significado"],
    [
        ["transferencia.iniciada", "pagos", "cuentas", "Hay que debitar la cuenta origen"],
        ["transferencia.debito-realizado", "cuentas", "cuentas", "Debito OK, hay que acreditar destino"],
        ["transferencia.debito-fallido", "cuentas", "pagos", "Fondos insuficientes (FALLIDA)"],
        ["transferencia.credito-fallido", "cuentas", "cuentas", "Destino inexistente, compensar"],
        ["transferencia.completada", "cuentas", "pagos, clientes", "Transferencia exitosa"],
        ["transferencia.revertida", "cuentas", "pagos", "Compensacion aplicada (REVERTIDA)"],
    ],
    col_widths=[4*cm, 2*cm, 3*cm, 7.8*cm],
))
story.append(Paragraph(
    "Todos los topics tienen 3 particiones y replicacion 1. La key de cada mensaje es el transaccionId "
    "(UUID), garantizando que todos los eventos de una transferencia caigan en la misma particion y se "
    "procesen en orden. Cada servicio tiene su propio group-id (pagos-estado, cuentas-saga, "
    "clientes-notificaciones).", s_body))

story.append(Paragraph("6.3 Idempotencia", s_h2))
story.append(Paragraph(
    "Cuentas mantiene un EstadoSagaRepository (en memoria, con compare-and-set) que registra el paso "
    "de cada transferencia. Si Kafka reentrega un mensaje, el listener lo detecta y lo ignora. Pagos "
    "solo actualiza el estado de una transferencia que sigue en PENDIENTE, rechazando cambios duplicados.", s_body))

story.append(PageBreak())

# ===== 7. SEGURIDAD =====
story.append(Paragraph("7. Seguridad con Spring Cloud Security (OAuth2)", s_h1))
story.append(Paragraph(
    "Implemente la seguridad con Spring Authorization Server 1.3.2, usando el flujo client_credentials "
    "que es el apropiado para comunicacion entre servicios (machine-to-machine).", s_body))

story.append(Paragraph("7.1 Arquitectura de seguridad", s_h2))
story.append(Paragraph(
    "<bullet>&bull;</bullet> El " + b("auth-server") + " emite tokens JWT firmados con RSA (clave generada "
    "al arrancar) y publica el JWKS en /.well-known/jwks.json. Los tokens tienen un TTL de 15 minutos.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> Cada " + b("BFF") + " obtiene un token client_credentials con su propio "
    "cliente y scope, y lo adjunta a las llamadas hacia los microservicios backend.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> Los " + b("microservicios") + " (cuentas, clientes, pagos) son Resource Servers "
    "OAuth2: validan la firma del JWT descargando el JWKS del auth-server y verifican el scope.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> El " + b("healthcheck") + " de cuentas, clientes y pagos (GET /actuator/health) "
    "esta excluido de la seguridad para que Docker pueda verificar la salud sin token.", s_bullet))

story.append(Paragraph("7.2 Clientes registrados", s_h2))
story.append(make_table(
    ["Cliente", "Scope", "Accede a"],
    [
        ["cliente-web", "web", "bff-web: cuentas completas, transacciones, transferencias"],
        ["cliente-movil", "movil", "bff-movil: cuenta resumida"],
        ["cliente-cajero", "cajero", "bff-cajero: saldo y retiro"],
    ],
    col_widths=[3.5*cm, 2.5*cm, 10.8*cm],
))

story.append(Paragraph("7.3 Validacion cruzada", s_h2))
story.append(Paragraph(
    "El e2e verifica que un token de un canal no pueda acceder a otro: un token con scope=web "
    "enviado al bff-movil recibe 403 Forbidden. Sin token, cualquier endpoint protegido devuelve 401. "
    "Esta validacion cubre los 6 servicios que actuan como Resource Server.", s_body))

# ===== 8. DOCKER =====
story.append(Paragraph("8. Contenerizacion y escalabilidad con Docker", s_h1))

story.append(Paragraph("8.1 Docker Compose", s_h2))
story.append(Paragraph(
    "El archivo docker-compose.yaml define los 10 servicios Java, un broker Kafka y un volumen "
    "nombrado (batch-data) para los datos del batch. Cada servicio tiene:", s_body))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Dockerfile multi-stage:") + " primera etapa compila con maven:3.9-eclipse-temurin-21, "
    "segunda etapa corre con eclipse-temurin:21-jre-alpine (~180 MB por imagen vs ~800 MB con JDK).", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Healthcheck:") + " curl contra /actuator/health con interval, timeout, retries "
    "y start_period configurados. Los BFF usan un cliente HTTP interno (HC_CLIENT) porque su /actuator/health "
    "exige token.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("depends_on con service_healthy:") + " garantiza el orden de arranque sin scripts "
    "de espera. Config Server arranca primero, luego Eureka, luego auth-server y kafka, y finalmente los "
    "servicios de negocio y BFF.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Perfil docker:") + " cada servicio activa el perfil Spring 'docker' via "
    "variable de entorno, que apunta las URLs a nombres de contenedor (kafka:9092 en vez de localhost:9092).", s_bullet))

story.append(Paragraph("8.2 Consumo de recursos", s_h2))
story.append(Paragraph(
    "Medido con docker stats en reposo despues del e2e (10 contenedores): " + b("3.94 GB de RAM total") + ". "
    "El servicio mas pesado es pagos (524 MB, por H2 + JPA + Kafka), el mas liviano es config-server (320 MB). "
    "Una instancia t3.large de AWS (8 GB) tiene margen suficiente.", s_body))

story.append(Paragraph("8.3 Escalabilidad horizontal", s_h2))
story.append(Paragraph(
    "Demostramos la escalabilidad horizontal levantando 2 replicas de bff-web detras de un nginx con "
    "round-robin. El override docker-compose.escala-bff.yaml usa deploy.replicas: 2 y !override "
    "(Compose >= 2.24) para reasignar puertos. Resultados:", s_body))
story.append(Paragraph(
    "<bullet>&bull;</bullet> Eureka muestra exactamente 2 instancias UP de BFF-WEB, cada una con su propio "
    "hostname (contenedor Docker).", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> 10 llamadas via nginx (localhost:8080): 5 atendidas por bff-web-1 y 5 por bff-web-2 "
    "(distribucion equilibrada con zone en el upstream).", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> Ambas replicas validan el mismo token OAuth2 (firman contra el mismo JWKS).", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> El e2e completo pasa despues de volver a 1 replica (sin residuos).", s_bullet))

story.append(PageBreak())

# ===== 9. EVIDENCIA =====
story.append(Paragraph("9. Evidencia de ejecucion", s_h1))
story.append(Paragraph(
    "Toda la evidencia se encuentra en evidencia/eft_docker/final/ del repositorio. A continuacion "
    "listo las pruebas realizadas y sus resultados:", s_body))

story.append(make_table(
    ["Prueba", "Archivo de evidencia", "Resultado"],
    [
        ["Stack completo (build + healthy)", "stack_completo.txt", "10/10 servicios healthy, perfil docker activo"],
        ["E2E docker (2 corridas)", "e2e_docker_1.txt, e2e_docker_2.txt", "95/95 OK en ambas corridas"],
        ["Recorrido de la saga", "saga_recorrido_docker.txt", "Transferencia exitosa + compensacion verificadas"],
        ["Circuit Breaker", "cb_debug_docker.txt", "CLOSED->OPEN->HALF_OPEN->CLOSED, 13/13 checks"],
        ["Batch (3 jobs)", "batch_desde_repo.txt", "3/3 jobs EXITO"],
        ["Batch falla permanente", "batch_pruebas_falla.txt", "4 intentos, exit 1, RestartCount 3"],
        ["Batch falla transitoria", "batch_pruebas_falla.txt", "Recuperacion al 2do intento, exit 0"],
        ["Escala bff-web", "escala_bff_web/", "2 replicas, balanceo 5/5, token valido en ambas"],
        ["Healthcheck sin token", "healthcheck_sin_token.txt", "cuentas/clientes/pagos healthy con auth-server caido"],
        ["Clon limpio", "clon_limpio.txt", "95/95 OK desde clon nuevo, sin dependencias locales"],
        ["Recursos y requisitos", "recursos_y_requisitos.txt", "3.94 GB RAM en reposo (10 contenedores)"],
    ],
    col_widths=[3.5*cm, 5*cm, 8.3*cm],
    wrap_cols={0, 1, 2},
))

# ===== 10. DESAFIOS =====
story.append(Paragraph("10. Desafios y soluciones", s_h1))

story.append(Paragraph("10.1 Token invalido tras reinicio del auth-server", s_h2))
story.append(Paragraph(
    "Al reiniciar el auth-server, este genera un nuevo par de claves RSA. Los tokens en cache de los "
    "BFF (firmados con la clave anterior) quedan invalidos, pero los Resource Servers siguen aceptandolos "
    "hasta que descargan el nuevo JWKS. Al reiniciar cuentas, este descarga las claves nuevas y rechaza "
    "el token cacheado del BFF, produciendo un 401 transitorio que dura hasta que el token vence (15 min). "
    "Documente este comportamiento como hallazgo (hallazgo_token_tras_reinicio_auth.txt) y lo reproduje "
    "de forma determinista. La solucion definitiva requiere persistir la clave RSA, pero esta fuera del "
    "alcance de la actividad.", s_body))

story.append(Paragraph("10.2 Orden de arranque sin scripts de espera", s_h2))
story.append(Paragraph(
    "En la primera version usaba scripts bash con sleep y curl para esperar a que los servicios estuvieran "
    "listos. Lo reemplace por depends_on con condition: service_healthy, que es la forma nativa de Compose. "
    "El unico costo es que cada servicio necesita un healthcheck funcional, lo que resolvi exponiendo "
    "/actuator/health sin token en los microservicios backend.", s_body))

story.append(Paragraph("10.3 Escalado con !override y replicas", s_h2))
story.append(Paragraph(
    "El primer intento de escala uso --scale en la linea de comandos, pero un docker compose up -d "
    "posterior (sin el flag) reconciliaba el servicio a 1 replica, perdiendo la segunda. La solucion "
    "fue declarar deploy.replicas: 2 en el override YAML y usar !override para reasignar los puertos "
    "del host (8092-8093 en vez de 8081). Esto requiere Compose >= 2.24.", s_body))

story.append(Paragraph("10.4 Eureka lease y nginx zone", s_h2))
story.append(Paragraph(
    "En la primera prueba de escala, nginx distribuia 10/0 (todas las peticiones a una sola replica). "
    "El problema era que nginx asignaba 12 workers y sin la directiva zone el upstream no compartia "
    "estado entre ellos. Al agregar zone en el upstream, la distribucion paso a 5/5.", s_body))

story.append(PageBreak())

# ===== 11. PROPUESTAS DE MEJORA =====
story.append(Paragraph("11. Propuestas de mejora", s_h1))
story.append(Paragraph(
    "Las siguientes mejoras quedan fuera del alcance de esta EFT pero representan los pasos "
    "naturales para llevar este ecosistema a produccion:", s_body))

story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Base de datos persistente (PostgreSQL/RDS):") + " reemplazar H2 en memoria "
    "en cuentas y pagos por una base compartida. Esto permite escalar esos servicios horizontalmente y "
    "que los saldos sobrevivan reinicios.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Gestion de secretos:") + " mover los secretos OAuth2 y las credenciales "
    "de Kafka a AWS Secrets Manager o HashiCorp Vault, eliminandolos del config-repo.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Clave RSA persistente:") + " guardar el par de claves del auth-server en "
    "un volumen o en Secrets Manager para que un reinicio no invalide los tokens en curso.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("HTTPS con ALB:") + " colocar un Application Load Balancer de AWS con "
    "certificado ACM delante de los BFF, terminando TLS en el balanceador.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Kafka gestionado (MSK):") + " reemplazar el broker standalone del compose "
    "por Amazon MSK para alta disponibilidad, replicacion y monitoreo integrado.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Orquestacion con ECS/EKS:") + " migrar de Docker Compose en una EC2 a ECS "
    "Fargate (o EKS) para auto-scaling, rolling updates y service mesh.", s_bullet))

# ===== 12. CONCLUSIONES =====
story.append(Paragraph("12. Conclusiones", s_h1))
story.append(Paragraph(
    "El proyecto demuestra una migracion completa del sistema legacy del Banco XYZ a una arquitectura "
    "moderna de microservicios. Los cinco ejes de migracion se implementaron y verificaron con evidencia:", s_body))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Batch:") + " 3 jobs con particionamiento, skip, retry y recuperacion "
    "ante fallos, ejecutados en Docker con volumen nombrado y restart policy.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("BFF:") + " 3 canales diferenciados en seguridad, contenido y peso de payload, "
    "con Circuit Breaker y fallback funcional.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Microservicios:") + " 3 servicios de negocio independientes con comunicacion "
    "asincrona via Kafka y consistencia eventual garantizada por la saga.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Seguridad:") + " OAuth2 con Spring Authorization Server, JWT con RSA, "
    "scopes por canal y validacion cruzada.", s_bullet))
story.append(Paragraph(
    "<bullet>&bull;</bullet> " + b("Docker:") + " 10 servicios contenerizados con builds multi-stage, "
    "healthchecks, arranque ordenado y escalabilidad horizontal demostrada.", s_bullet))
story.append(Paragraph(
    "Todo se verifico con una prueba automatizada de 95 verificaciones, ejecutada en un clon limpio "
    "del repositorio sin dependencias locales.", s_body))


# ---------- generar ----------
out_path = "informe_tecnico_eft.pdf"

doc = SimpleDocTemplate(
    out_path,
    pagesize=letter,
    topMargin=2 * cm,
    bottomMargin=2 * cm,
    leftMargin=2.5 * cm,
    rightMargin=2.5 * cm,
    title="Informe Tecnico EFT — Banco XYZ",
    author="Sergio Mascareno",
)

# Numero de pagina en el footer
def footer(canvas_obj, doc_obj):
    canvas_obj.saveState()
    canvas_obj.setFont(FONT, 8)
    canvas_obj.setFillColor(HexColor("#888888"))
    canvas_obj.drawCentredString(
        doc_obj.pagesize[0] / 2, 1.2 * cm,
        f"Banco XYZ — Informe Tecnico EFT — Pagina {doc_obj.page}"
    )
    canvas_obj.restoreState()

doc.build(story, onFirstPage=footer, onLaterPages=footer)
print(f"PDF generado: {out_path}")
print(f"Tamanio: {os.path.getsize(out_path) / 1024:.0f} KB")
