-- Esquema de negocio para banco-xyz-batch.
-- Compatible con H2 (perfil dev) y PostgreSQL (perfil postgres).
-- Las tablas de metadata de Spring Batch (BATCH_JOB_INSTANCE, etc.) se
-- crean aparte de forma automatica via spring.batch.jdbc.initialize-schema.

-- ===================== Job 1: Reporte de Transacciones Diarias =====================

CREATE TABLE IF NOT EXISTS transaccion_validada (
    id      BIGINT PRIMARY KEY,
    fecha   DATE           NOT NULL,
    monto   DECIMAL(15, 2) NOT NULL,
    tipo    VARCHAR(20)    NOT NULL,
    -- Semana 2: reemplaza la deduplicacion en memoria (Set) de la semana 1,
    -- que dependia de chunk(1) para ser confiable. Con chunk(5) + 3 hilos
    -- en paralelo, la unicidad debe garantizarla la base de datos, no el
    -- orden de ejecucion en memoria. Ver TransaccionItemProcessor.
    CONSTRAINT uk_transaccion_clave_negocio UNIQUE (fecha, monto, tipo)
);

CREATE TABLE IF NOT EXISTS resumen_transacciones_diarias (
    fecha                   DATE PRIMARY KEY,
    total_creditos          DECIMAL(15, 2) NOT NULL,
    total_debitos           DECIMAL(15, 2) NOT NULL,
    cantidad_transacciones  INT            NOT NULL,
    monto_total             DECIMAL(15, 2) NOT NULL
);

-- ===================== Job 2: Calculo de Intereses Mensuales =====================

CREATE TABLE IF NOT EXISTS cuenta_interes_mensual (
    cuenta_id          BIGINT PRIMARY KEY,
    nombre             VARCHAR(150)   NOT NULL,
    tipo               VARCHAR(20)    NOT NULL,
    edad               INT            NOT NULL,
    saldo_inicial      DECIMAL(15, 2) NOT NULL,
    tasa_interes       DECIMAL(6, 4)  NOT NULL,
    interes_generado   DECIMAL(15, 2) NOT NULL,
    saldo_final        DECIMAL(15, 2) NOT NULL,
    fecha_proceso      TIMESTAMP      NOT NULL,
    -- Semana 2: mismo razonamiento que uk_transaccion_clave_negocio. El
    -- duplicado real del dataset no repite cuenta_id (que ya es PK), repite
    -- nombre+saldo+edad+tipo. Ver InteresItemProcessor.
    CONSTRAINT uk_interes_clave_contenido UNIQUE (nombre, saldo_inicial, edad, tipo)
);

-- ===================== Job 3: Estados de Cuenta Anuales =====================

CREATE TABLE IF NOT EXISTS movimiento_cuenta_anual (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cuenta_id    BIGINT         NOT NULL,
    fecha        DATE           NOT NULL,
    transaccion  VARCHAR(20)    NOT NULL,
    monto        DECIMAL(15, 2) NOT NULL,
    descripcion  VARCHAR(255)   NOT NULL
);

CREATE TABLE IF NOT EXISTS estado_cuenta_anual (
    cuenta_id             BIGINT    NOT NULL,
    anio                  INT       NOT NULL,
    total_depositos       DECIMAL(15, 2) NOT NULL,
    total_retiros         DECIMAL(15, 2) NOT NULL,
    total_compras         DECIMAL(15, 2) NOT NULL,
    saldo_neto            DECIMAL(15, 2) NOT NULL,
    cantidad_movimientos  INT       NOT NULL,
    fecha_generacion      TIMESTAMP NOT NULL,
    PRIMARY KEY (cuenta_id, anio)
);

-- ===================== Auditoria transversal de anomalias =====================

CREATE TABLE IF NOT EXISTS anomalia_dato (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_name         VARCHAR(60)  NOT NULL,
    origen           VARCHAR(60)  NOT NULL,
    detalle          VARCHAR(500) NOT NULL,
    dato_crudo       VARCHAR(500),
    fecha_deteccion  TIMESTAMP    NOT NULL
);
