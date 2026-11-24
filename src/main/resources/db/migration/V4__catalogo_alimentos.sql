-- V4: catálogo de categorías y alimentos (§6.1 y §8.6 de docs/PROJECT_PROPOSAL.md)
-- Las categorías iniciales se cargan en la migración repetible R__categorias.sql.

-- ---------------------------------------------------------------------------------------------
-- categoria
-- ---------------------------------------------------------------------------------------------
CREATE TABLE categoria (
    id             uuid        NOT NULL,
    nombre         varchar(80) NOT NULL,
    tipo           varchar(10) NOT NULL,
    padre_id       uuid        NULL,
    seccion_compra varchar(40) NULL,
    orden          smallint    NOT NULL DEFAULT 0,
    creado_en      timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT pk_categoria PRIMARY KEY (id),
    CONSTRAINT uk_categoria_tipo_nombre UNIQUE (tipo, nombre),
    CONSTRAINT fk_categoria_padre FOREIGN KEY (padre_id) REFERENCES categoria (id),
    CONSTRAINT ck_categoria_tipo CHECK (tipo IN ('ALIMENTO', 'RECETA')),
    -- La sección del supermercado solo tiene sentido en las categorías de alimentos
    CONSTRAINT ck_categoria_seccion_compra CHECK (tipo = 'ALIMENTO' OR seccion_compra IS NULL)
);

CREATE INDEX ix_categoria_padre_id ON categoria (padre_id);

COMMENT ON TABLE categoria IS
    'Categorías de alimentos y de recetas (jerárquicas). seccion_compra agrupa la lista de la compra por sección del supermercado.';

-- ---------------------------------------------------------------------------------------------
-- alimento
-- ---------------------------------------------------------------------------------------------
CREATE TABLE alimento (
    id                  uuid         NOT NULL,
    nombre              varchar(150) NOT NULL,
    nombre_normalizado  varchar(150) NOT NULL,
    marca               varchar(80)  NULL,
    categoria_id        uuid         NOT NULL,
    codigo_barras       varchar(14)  NULL,
    preparacion         varchar(10)  NOT NULL DEFAULT 'OTRA',
    kcal100             numeric(7,2) NOT NULL,
    proteinas100        numeric(7,2) NOT NULL,
    carbohidratos100    numeric(7,2) NOT NULL,
    grasas100           numeric(7,2) NOT NULL,
    -- NULL = dato no disponible (habitual en Open Food Facts), nunca 0
    azucares100         numeric(7,2) NULL,
    saturadas100        numeric(7,2) NULL,
    fibra100            numeric(7,2) NULL,
    sal100              numeric(7,2) NULL,
    unidad_base         varchar(2)   NOT NULL,
    porciones           jsonb        NOT NULL DEFAULT '[]',
    alergenos_contiene  text[]       NOT NULL DEFAULT '{}',
    alergenos_trazas    text[]       NOT NULL DEFAULT '{}',
    alergenos_revisados boolean      NOT NULL DEFAULT false,
    contiene_lactosa    boolean      NOT NULL DEFAULT false,
    apto_vegano         boolean      NOT NULL,
    apto_vegetariano    boolean      NOT NULL,
    apto_pescetariano   boolean      NOT NULL,
    fuente              varchar(20)  NOT NULL,
    referencia_externa  varchar(80)  NULL,
    visibilidad         varchar(10)  NOT NULL,
    propietario_id      uuid         NULL,
    validado            boolean      NOT NULL DEFAULT false,
    activo              boolean      NOT NULL DEFAULT true,
    creado_en           timestamptz  NOT NULL,
    actualizado_en      timestamptz  NOT NULL,
    CONSTRAINT pk_alimento PRIMARY KEY (id),
    CONSTRAINT uk_alimento_codigo_barras UNIQUE (codigo_barras),
    -- RESTRICT: una categoría con alimentos no se puede borrar
    CONSTRAINT fk_alimento_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id),
    CONSTRAINT fk_alimento_propietario FOREIGN KEY (propietario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_alimento_preparacion
        CHECK (preparacion IN ('CRUDO', 'COCIDO', 'PLANCHA', 'FRITO', 'HORNO', 'ENVASADO', 'OTRA')),
    CONSTRAINT ck_alimento_kcal100 CHECK (kcal100 >= 0),
    CONSTRAINT ck_alimento_proteinas100 CHECK (proteinas100 >= 0),
    CONSTRAINT ck_alimento_carbohidratos100 CHECK (carbohidratos100 >= 0),
    CONSTRAINT ck_alimento_grasas100 CHECK (grasas100 >= 0),
    CONSTRAINT ck_alimento_azucares100 CHECK (azucares100 >= 0),
    CONSTRAINT ck_alimento_saturadas100 CHECK (saturadas100 >= 0),
    CONSTRAINT ck_alimento_fibra100 CHECK (fibra100 >= 0),
    CONSTRAINT ck_alimento_sal100 CHECK (sal100 >= 0),
    CONSTRAINT ck_alimento_unidad_base CHECK (unidad_base IN ('G', 'ML')),
    CONSTRAINT ck_alimento_porciones CHECK (jsonb_typeof(porciones) = 'array'),
    -- Solo los 14 alérgenos del Reglamento (UE) 1169/2011 (los mismos que en preferencia_alimentaria)
    CONSTRAINT ck_alimento_alergenos_contiene CHECK (alergenos_contiene <@ ARRAY[
        'GLUTEN', 'CRUSTACEOS', 'HUEVOS', 'PESCADO', 'CACAHUETES', 'SOJA', 'LACTEOS',
        'FRUTOS_DE_CASCARA', 'APIO', 'MOSTAZA', 'SESAMO', 'SULFITOS', 'ALTRAMUCES', 'MOLUSCOS']::text[]),
    CONSTRAINT ck_alimento_alergenos_trazas CHECK (alergenos_trazas <@ ARRAY[
        'GLUTEN', 'CRUSTACEOS', 'HUEVOS', 'PESCADO', 'CACAHUETES', 'SOJA', 'LACTEOS',
        'FRUTOS_DE_CASCARA', 'APIO', 'MOSTAZA', 'SESAMO', 'SULFITOS', 'ALTRAMUCES', 'MOLUSCOS']::text[]),
    CONSTRAINT ck_alimento_fuente
        CHECK (fuente IN ('BEDCA', 'USDA', 'OPEN_FOOD_FACTS', 'ADMIN', 'USUARIO', 'OCR')),
    CONSTRAINT ck_alimento_visibilidad CHECK (visibilidad IN ('PUBLICO', 'PRIVADO')),
    CONSTRAINT ck_alimento_privado_con_propietario CHECK (visibilidad <> 'PRIVADO' OR propietario_id IS NOT NULL)
);

-- Búsqueda tolerante a tildes y errores de escritura (pg_trgm se creó en V1)
CREATE INDEX ix_alimento_nombre_normalizado_trgm ON alimento USING gin (nombre_normalizado gin_trgm_ops);
CREATE INDEX ix_alimento_categoria_id ON alimento (categoria_id);
CREATE INDEX ix_alimento_propietario_id ON alimento (propietario_id);

COMMENT ON TABLE alimento IS
    'Catálogo de alimentos con valores por 100 g o 100 ml y su fuente (trazabilidad, OBJ-04). Los usados en registros no se borran: activo = false.';
COMMENT ON COLUMN alimento.nombre_normalizado IS
    'Nombre en minúsculas, sin tildes y con los espacios colapsados; lo rellena la aplicación.';
COMMENT ON COLUMN alimento.porciones IS
    'Medidas caseras: [{"nombre": "unidad mediana", "gramos": 120}].';
COMMENT ON COLUMN alimento.alergenos_revisados IS
    'false hasta que un administrador revisa los alérgenos; si es false se excluye de los menús de usuarios con alergias estrictas.';

-- ---------------------------------------------------------------------------------------------
-- FK pendientes de V3 (preferencias de rechazo y favorito)
-- ---------------------------------------------------------------------------------------------
ALTER TABLE preferencia_alimentaria
    ADD CONSTRAINT fk_preferencia_alimentaria_alimento
        FOREIGN KEY (alimento_id) REFERENCES alimento (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_preferencia_alimentaria_categoria
        FOREIGN KEY (categoria_id) REFERENCES categoria (id) ON DELETE CASCADE;

CREATE INDEX ix_preferencia_alimentaria_alimento_id ON preferencia_alimentaria (alimento_id);
CREATE INDEX ix_preferencia_alimentaria_categoria_id ON preferencia_alimentaria (categoria_id);
