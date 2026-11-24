-- V8: registro de uso de la IA (§5.7 UsoIA): tokens, latencia y coste estimado de cada llamada al
-- proveedor, para controlar el gasto y aplicar los límites diarios por usuario.

CREATE TABLE uso_ia (
    id             uuid          NOT NULL,
    usuario_id     uuid          NULL,
    funcion        varchar(20)   NOT NULL,
    proveedor      varchar(40)   NOT NULL,
    modelo         varchar(80)   NULL,
    tokens_entrada int           NOT NULL DEFAULT 0,
    tokens_salida  int           NOT NULL DEFAULT 0,
    latencia_ms    int           NOT NULL,
    coste_estimado numeric(10,6) NOT NULL DEFAULT 0,
    exito          boolean       NOT NULL,
    creado_en      timestamptz   NOT NULL,
    actualizado_en timestamptz   NOT NULL,
    CONSTRAINT pk_uso_ia PRIMARY KEY (id),
    -- Nulo en las llamadas que no hace un usuario (tareas del sistema)
    CONSTRAINT fk_uso_ia_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_uso_ia_funcion CHECK (funcion IN ('ANALISIS_IMAGEN', 'ETIQUETA', 'ASISTENTE', 'TEXTO')),
    CONSTRAINT ck_uso_ia_valores CHECK (tokens_entrada >= 0 AND tokens_salida >= 0 AND latencia_ms >= 0
        AND coste_estimado >= 0)
);

CREATE INDEX ix_uso_ia_usuario_id_funcion_creado_en ON uso_ia (usuario_id, funcion, creado_en);

COMMENT ON TABLE uso_ia IS
    'Una fila por llamada al proveedor de IA (también las fallidas). No guarda el contenido enviado ni la respuesta.';
COMMENT ON COLUMN uso_ia.coste_estimado IS
    'En USD: tokens × precio por millón de app.ia.coste-entrada-millon / coste-salida-millon.';
