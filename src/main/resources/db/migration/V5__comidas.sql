-- V5: registro de comidas (§6.1 RegistroComida y AlimentoRegistrado, §6.3 de docs/PROJECT_PROPOSAL.md)
-- Cada alimento registrado guarda una instantánea de sus nutrientes: si después se corrige el alimento
-- del catálogo, el historial no cambia. Los totales de la comida están desnormalizados y los recalcula
-- el servicio en la misma transacción.

-- ---------------------------------------------------------------------------------------------
-- registro_comida
-- ---------------------------------------------------------------------------------------------
CREATE TABLE registro_comida (
    id                  uuid         NOT NULL,
    usuario_id          uuid         NOT NULL,
    fecha               date         NOT NULL,
    hora                time         NULL,
    tipo_comida         varchar(15)  NOT NULL,
    origen              varchar(14)  NOT NULL,
    notas               varchar(500) NULL,
    total_kcal          numeric(8,1) NOT NULL DEFAULT 0,
    total_proteina      numeric(8,1) NOT NULL DEFAULT 0,
    total_carbohidratos numeric(8,1) NOT NULL DEFAULT 0,
    total_grasa         numeric(8,1) NOT NULL DEFAULT 0,
    creado_en           timestamptz  NOT NULL,
    actualizado_en      timestamptz  NOT NULL,
    CONSTRAINT pk_registro_comida PRIMARY KEY (id),
    CONSTRAINT fk_registro_comida_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_registro_comida_tipo_comida CHECK (tipo_comida IN
        ('DESAYUNO', 'MEDIA_MANANA', 'COMIDA', 'MERIENDA', 'CENA', 'PRE_ENTRENO', 'POST_ENTRENO', 'OTRO')),
    CONSTRAINT ck_registro_comida_origen CHECK (origen IN
        ('MANUAL', 'FOTO', 'PLAN', 'RECETA', 'COPIA', 'CODIGO_BARRAS')),
    CONSTRAINT ck_registro_comida_totales CHECK (total_kcal >= 0 AND total_proteina >= 0
        AND total_carbohidratos >= 0 AND total_grasa >= 0)
);

CREATE INDEX ix_registro_comida_usuario_id_fecha ON registro_comida (usuario_id, fecha);

COMMENT ON TABLE registro_comida IS
    'Comida registrada por un usuario en una fecha. Los totales son la suma de sus alimentos (desnormalizados, §6.3).';

-- ---------------------------------------------------------------------------------------------
-- alimento_registrado
-- ---------------------------------------------------------------------------------------------
CREATE TABLE alimento_registrado (
    id                 uuid         NOT NULL,
    registro_comida_id uuid         NOT NULL,
    alimento_id        uuid         NOT NULL,
    cantidad           numeric(8,2) NOT NULL,
    unidad             varchar(40)  NOT NULL,
    gramos             numeric(7,1) NOT NULL,
    kcal               numeric(7,1) NOT NULL,
    proteina           numeric(7,1) NOT NULL,
    carbohidratos      numeric(7,1) NOT NULL,
    grasa              numeric(7,1) NOT NULL,
    origen_ia          boolean      NOT NULL DEFAULT false,
    confianza_ia       numeric(3,2) NULL,
    orden              smallint     NOT NULL,
    creado_en          timestamptz  NOT NULL,
    actualizado_en     timestamptz  NOT NULL,
    CONSTRAINT pk_alimento_registrado PRIMARY KEY (id),
    CONSTRAINT fk_alimento_registrado_registro_comida
        FOREIGN KEY (registro_comida_id) REFERENCES registro_comida (id) ON DELETE CASCADE,
    -- Un alimento usado en el historial no se puede borrar: se desactiva (§6.3)
    CONSTRAINT fk_alimento_registrado_alimento
        FOREIGN KEY (alimento_id) REFERENCES alimento (id) ON DELETE RESTRICT,
    CONSTRAINT ck_alimento_registrado_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_alimento_registrado_gramos CHECK (gramos > 0 AND gramos <= 5000),
    CONSTRAINT ck_alimento_registrado_nutrientes CHECK (kcal >= 0 AND proteina >= 0
        AND carbohidratos >= 0 AND grasa >= 0),
    CONSTRAINT ck_alimento_registrado_confianza_ia CHECK (confianza_ia BETWEEN 0 AND 1),
    CONSTRAINT ck_alimento_registrado_orden CHECK (orden >= 0)
);

CREATE INDEX ix_alimento_registrado_registro_comida_id ON alimento_registrado (registro_comida_id);
-- Para comprobar si un alimento está en uso y para los alimentos recientes y frecuentes
CREATE INDEX ix_alimento_registrado_alimento_id ON alimento_registrado (alimento_id);

COMMENT ON TABLE alimento_registrado IS
    'Alimento de una comida con la cantidad introducida, sus gramos y una instantánea de sus nutrientes en el momento del registro.';
COMMENT ON COLUMN alimento_registrado.unidad IS
    'Unidad introducida por el usuario: g, ml o el nombre de una porción casera ("unidad mediana").';
