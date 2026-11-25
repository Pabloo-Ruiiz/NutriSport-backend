-- V17: patrones detectados (insights) y su interruptor en la cuenta (Ampliación, §4.6 y §6.1 de docs/PROJECT_PROPOSAL.md).
-- La guía la llama V15, pero V15 (sugerencias de la IA) y V16 (lista de la compra) ya existen: es la siguiente
-- versión libre.

CREATE TABLE insight (
    id              uuid          NOT NULL,
    usuario_id      uuid          NOT NULL,
    tipo            varchar(40)   NOT NULL,
    severidad       varchar(12)   NOT NULL,
    titulo          varchar(120)  NOT NULL,
    texto           varchar(600)  NOT NULL,
    -- Cifras ya calculadas por las reglas ({"diferenciaPct": 18, "gramosFaltantes": 63, ...}); el texto sale de ellas
    datos           jsonb         NOT NULL DEFAULT '{}',
    -- Días analizados
    periodo_desde   date          NOT NULL,
    periodo_hasta   date          NOT NULL,
    -- Solo los de la última detección están vigentes
    vigente         boolean       NOT NULL DEFAULT true,
    creado_en       timestamptz   NOT NULL,
    actualizado_en  timestamptz   NOT NULL,
    CONSTRAINT pk_insight PRIMARY KEY (id),
    CONSTRAINT fk_insight_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_insight_tipo CHECK (tipo IN ('PROTEINA_BAJA', 'CARBOHIDRATOS_ENTRENO_BAJOS',
        'REPARTO_PROTEINA_BUENO', 'DIAS_SIN_REGISTRO', 'TENDENCIA_PESO')),
    CONSTRAINT ck_insight_severidad CHECK (severidad IN ('INFO', 'POSITIVO', 'ADVERTENCIA')),
    CONSTRAINT ck_insight_datos CHECK (jsonb_typeof(datos) = 'object'),
    CONSTRAINT ck_insight_periodo CHECK (periodo_hasta >= periodo_desde)
);

-- Insights vigentes de un usuario, los más recientes primero
CREATE INDEX ix_insight_usuario_vigente ON insight (usuario_id, vigente, creado_en);

COMMENT ON TABLE insight IS
    'Patrón detectado por las reglas estadísticas de §4.6 en los datos de un usuario. Las cifras están en datos; título y texto se redactan a partir de ellas (la IA, si se usa, solo añade un consejo sin cifras nuevas).';

-- El usuario puede desactivar los insights desde su cuenta
ALTER TABLE usuario ADD COLUMN insights_activos boolean NOT NULL DEFAULT true;

COMMENT ON COLUMN usuario.insights_activos IS 'Si la tarea nocturna detecta patrones para este usuario.';
