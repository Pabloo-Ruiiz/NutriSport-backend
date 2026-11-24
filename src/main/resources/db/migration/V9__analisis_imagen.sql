-- V9: análisis de fotos con IA (§4.3 y §6.1 AnalisisImagen de docs/PROJECT_PROPOSAL.md)
-- Ciclo: PENDIENTE → PROCESANDO → COMPLETADO | FALLIDO; el usuario lo pasa a CONFIRMADO o DESCARTADO.

CREATE TABLE analisis_imagen (
    id                  uuid          NOT NULL,
    usuario_id          uuid          NOT NULL,
    tipo                varchar(10)   NOT NULL,
    -- NULL cuando la foto se elimina por retención (§5.8)
    imagen_key          varchar(300)  NULL,
    miniatura_key       varchar(300)  NULL,
    estado              varchar(12)   NOT NULL,
    tipo_comida         varchar(15)   NULL,
    descripcion_usuario varchar(500)  NULL,
    -- Reproducibilidad: qué proveedor, modelo y versión del prompt dieron el resultado
    proveedor           varchar(40)   NULL,
    modelo              varchar(80)   NULL,
    version_prompt      varchar(20)   NULL,
    resultado_bruto     jsonb         NULL,
    candidatos          jsonb         NULL,
    correcciones        jsonb         NULL,
    latencia_ms         int           NULL,
    coste_estimado      numeric(10,6) NULL,
    registro_comida_id  uuid          NULL,
    alimento_creado_id  uuid          NULL,
    error_mensaje       varchar(300)  NULL,
    completado_en       timestamptz   NULL,
    creado_en           timestamptz   NOT NULL,
    actualizado_en      timestamptz   NOT NULL,
    CONSTRAINT pk_analisis_imagen PRIMARY KEY (id),
    CONSTRAINT fk_analisis_imagen_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_analisis_imagen_registro_comida
        FOREIGN KEY (registro_comida_id) REFERENCES registro_comida (id) ON DELETE SET NULL,
    CONSTRAINT fk_analisis_imagen_alimento_creado
        FOREIGN KEY (alimento_creado_id) REFERENCES alimento (id) ON DELETE SET NULL,
    CONSTRAINT ck_analisis_imagen_tipo CHECK (tipo IN ('COMIDA', 'ETIQUETA')),
    CONSTRAINT ck_analisis_imagen_estado CHECK (estado IN
        ('PENDIENTE', 'PROCESANDO', 'COMPLETADO', 'FALLIDO', 'CONFIRMADO', 'DESCARTADO')),
    CONSTRAINT ck_analisis_imagen_tipo_comida CHECK (tipo_comida IN
        ('DESAYUNO', 'MEDIA_MANANA', 'COMIDA', 'MERIENDA', 'CENA', 'PRE_ENTRENO', 'POST_ENTRENO', 'OTRO')),
    CONSTRAINT ck_analisis_imagen_valores CHECK (latencia_ms >= 0 AND coste_estimado >= 0)
);

CREATE INDEX ix_analisis_imagen_usuario_id_creado_en ON analisis_imagen (usuario_id, creado_en);
-- Para la tarea que marca como FALLIDOS los análisis atascados en PENDIENTE o PROCESANDO
CREATE INDEX ix_analisis_imagen_estado_creado_en ON analisis_imagen (estado, creado_en);
CREATE INDEX ix_analisis_imagen_registro_comida_id ON analisis_imagen (registro_comida_id);
CREATE INDEX ix_analisis_imagen_alimento_creado_id ON analisis_imagen (alimento_creado_id);

COMMENT ON TABLE analisis_imagen IS
    'Análisis de una foto con IA. La IA solo identifica alimentos y estima cantidades; los nutrientes salen del catálogo (§4.1).';
COMMENT ON COLUMN analisis_imagen.correcciones IS
    'Diferencias entre la propuesta de la IA y lo que confirmó el usuario, para medir la precisión real.';
