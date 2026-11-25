-- V13: emparejamiento reutilizable entre los nombres de ingredientes de TheMealDB y los alimentos del catálogo
-- (Ampliación, §6.1 de docs/PROJECT_PROPOSAL.md y §6.2 y §7 de docs/API_INTEGRATIONS.md). La guía la llama V12,
-- pero V12 ya son las recetas: es la siguiente versión libre.

CREATE TABLE mapeo_ingrediente (
    id                  uuid         NOT NULL,
    -- Nombre del ingrediente normalizado (minúsculas y sin tildes): "chicken breast"
    nombre_normalizado  varchar(100) NOT NULL,
    -- NULL mientras no hay una coincidencia (pendiente de revisión por el administrador)
    alimento_id         uuid         NULL,
    metodo              varchar(10)  NOT NULL,
    -- Similitud de texto de la coincidencia elegida (0–1); NULL si no la hay o el emparejamiento es manual
    confianza           numeric(3,2) NULL,
    revisado            boolean      NOT NULL DEFAULT false,
    creado_en           timestamptz  NOT NULL,
    actualizado_en      timestamptz  NOT NULL,
    CONSTRAINT pk_mapeo_ingrediente PRIMARY KEY (id),
    CONSTRAINT uk_mapeo_ingrediente_nombre UNIQUE (nombre_normalizado),
    -- Si se borra el alimento, el emparejamiento vuelve a quedar pendiente
    CONSTRAINT fk_mapeo_ingrediente_alimento FOREIGN KEY (alimento_id) REFERENCES alimento (id) ON DELETE SET NULL,
    CONSTRAINT ck_mapeo_ingrediente_metodo CHECK (metodo IN ('AUTOMATICO', 'MANUAL')),
    CONSTRAINT ck_mapeo_ingrediente_confianza CHECK (confianza IS NULL OR confianza BETWEEN 0 AND 1)
);

-- Para el ON DELETE SET NULL y para la cola de revisión del administrador
CREATE INDEX ix_mapeo_ingrediente_alimento_id ON mapeo_ingrediente (alimento_id);
CREATE INDEX ix_mapeo_ingrediente_pendientes ON mapeo_ingrediente (revisado) WHERE revisado = false;

COMMENT ON TABLE mapeo_ingrediente IS
    'Emparejamiento de un nombre de ingrediente (TheMealDB) con un alimento del catálogo, que puede venir de USDA. Evita repetir búsquedas y el administrador lo puede corregir (MANUAL).';
