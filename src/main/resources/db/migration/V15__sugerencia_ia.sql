-- V15: sugerencias de la IA para una comida planificada (Ampliación, §4.5 "Personalización con IA" de
-- docs/PROJECT_PROPOSAL.md). La IA solo propone un ajuste de cantidad o una sustitución de un ingrediente; el backend
-- la valida (alimento del catálogo, restricciones y alérgenos) y calcula las cifras. Se guarda para poder aplicarla
-- después (POST .../sugerencia-ia/aplicar), que crea una receta propia derivada.

CREATE TABLE sugerencia_ia (
    id                     uuid          NOT NULL,
    usuario_id             uuid          NOT NULL,
    comida_planificada_id  uuid          NOT NULL,
    -- La receta de la comida cuando se pidió la sugerencia (si la comida cambia de receta, queda obsoleta)
    receta_id              uuid          NOT NULL,
    -- AJUSTE_CANTIDAD o SUSTITUCION; nulo si la IA no propuso nada utilizable
    tipo                   varchar(16)   NULL,
    -- Ingrediente de la receta afectado, alimento del catálogo y gramos propuestos (solo en las válidas)
    receta_ingrediente_id  uuid          NULL,
    alimento_id            uuid          NULL,
    gramos                 numeric(7,1)  NULL,
    -- Texto redactado por el backend con las cifras que calcula él (o el motivo del descarte)
    texto                  varchar(500)  NOT NULL,
    delta_kcal_racion      numeric(7,1)  NULL,
    delta_proteina_racion  numeric(7,1)  NULL,
    valida                 boolean       NOT NULL,
    aplicada_en            timestamptz   NULL,
    -- Receta propia creada al aplicarla
    receta_derivada_id     uuid          NULL,
    creado_en              timestamptz   NOT NULL,
    actualizado_en         timestamptz   NOT NULL,
    CONSTRAINT pk_sugerencia_ia PRIMARY KEY (id),
    CONSTRAINT fk_sugerencia_ia_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_sugerencia_ia_comida FOREIGN KEY (comida_planificada_id)
        REFERENCES comida_planificada (id) ON DELETE CASCADE,
    CONSTRAINT fk_sugerencia_ia_receta FOREIGN KEY (receta_id) REFERENCES receta (id) ON DELETE CASCADE,
    CONSTRAINT fk_sugerencia_ia_ingrediente FOREIGN KEY (receta_ingrediente_id)
        REFERENCES receta_ingrediente (id) ON DELETE CASCADE,
    CONSTRAINT fk_sugerencia_ia_alimento FOREIGN KEY (alimento_id) REFERENCES alimento (id) ON DELETE CASCADE,
    CONSTRAINT fk_sugerencia_ia_receta_derivada FOREIGN KEY (receta_derivada_id)
        REFERENCES receta (id) ON DELETE SET NULL,
    CONSTRAINT ck_sugerencia_ia_tipo CHECK (tipo IS NULL OR tipo IN ('AJUSTE_CANTIDAD', 'SUSTITUCION')),
    CONSTRAINT ck_sugerencia_ia_gramos CHECK (gramos IS NULL OR gramos > 0),
    -- Una válida tiene todo lo necesario para aplicarla
    CONSTRAINT ck_sugerencia_ia_valida CHECK (NOT valida OR (tipo IS NOT NULL AND receta_ingrediente_id IS NOT NULL
        AND alimento_id IS NOT NULL AND gramos IS NOT NULL AND delta_kcal_racion IS NOT NULL
        AND delta_proteina_racion IS NOT NULL)),
    -- Solo se aplica una válida
    CONSTRAINT ck_sugerencia_ia_aplicada CHECK (aplicada_en IS NULL OR valida)
);

-- Para las FK al borrar comidas, recetas, ingredientes y alimentos
CREATE INDEX ix_sugerencia_ia_comida ON sugerencia_ia (comida_planificada_id);
CREATE INDEX ix_sugerencia_ia_usuario ON sugerencia_ia (usuario_id);
CREATE INDEX ix_sugerencia_ia_receta ON sugerencia_ia (receta_id);
CREATE INDEX ix_sugerencia_ia_ingrediente ON sugerencia_ia (receta_ingrediente_id);
CREATE INDEX ix_sugerencia_ia_alimento ON sugerencia_ia (alimento_id);
CREATE INDEX ix_sugerencia_ia_receta_derivada ON sugerencia_ia (receta_derivada_id);

COMMENT ON TABLE sugerencia_ia IS
    'Ajuste o sustitución de un ingrediente propuesto por la IA para una comida planificada, validado y calculado por el backend.';
