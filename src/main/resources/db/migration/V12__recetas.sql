-- V12: recetas propias e importadas de TheMealDB (Ampliación, §6.1 de docs/PROJECT_PROPOSAL.md y §4 de
-- docs/API_INTEGRATIONS.md). La guía la llama V11, pero V10 y V11 ya están ocupadas (auditoría y fuentes
-- externas): es la siguiente versión libre.

-- ---------------------------------------------------------------------------------------------
-- Categoría equivalente de TheMealDB (solo en categorías de recetas)
-- ---------------------------------------------------------------------------------------------
ALTER TABLE categoria
    ADD COLUMN codigo_externo varchar(40) NULL,
    ADD CONSTRAINT ck_categoria_codigo_externo CHECK (tipo = 'RECETA' OR codigo_externo IS NULL);

-- Una strCategory de TheMealDB corresponde a una sola categoría
CREATE UNIQUE INDEX uk_categoria_codigo_externo ON categoria (codigo_externo) WHERE codigo_externo IS NOT NULL;

COMMENT ON COLUMN categoria.codigo_externo IS
    'strCategory de TheMealDB equivalente (p. ej. Chicken para Pollo). Solo en categorías de recetas.';

-- ---------------------------------------------------------------------------------------------
-- receta
-- ---------------------------------------------------------------------------------------------
CREATE TABLE receta (
    id                      uuid         NOT NULL,
    origen                  varchar(10)  NOT NULL,
    -- idMeal de TheMealDB; NULL en las recetas propias
    referencia_externa      varchar(20)  NULL,
    nombre                  varchar(150) NOT NULL,
    nombre_normalizado      varchar(150) NOT NULL,
    descripcion             text         NULL,
    idioma                  varchar(5)   NOT NULL DEFAULT 'es',
    categoria_id            uuid         NULL,
    -- strCategory y strArea originales de TheMealDB
    categoria_externa       varchar(40)  NULL,
    area                    varchar(40)  NULL,
    -- strMealThumb: la imagen no se copia, se enlaza con atribución
    imagen_url              varchar(300) NULL,
    raciones                smallint     NOT NULL DEFAULT 4,
    -- true mientras las raciones no las fije el administrador (TheMealDB no las informa)
    raciones_supuestas      boolean      NOT NULL DEFAULT true,
    tiempo_preparacion_min  smallint     NULL,
    dificultad              varchar(10)  NULL,
    -- Constantes de TipoComida: evita proponer lentejas en el desayuno
    franjas_aptas           text[]       NOT NULL DEFAULT '{}',
    -- [{"orden": 1, "texto": "..."}]
    pasos                   jsonb        NOT NULL DEFAULT '[]',
    etiquetas               text[]       NOT NULL DEFAULT '{}',
    -- Calculados a partir de los ingredientes; NULL mientras no hay cálculo
    kcal_racion             numeric(7,1) NULL,
    proteina_racion         numeric(7,1) NULL,
    carbohidratos_racion    numeric(7,1) NULL,
    grasa_racion            numeric(7,1) NULL,
    estado_nutricional      varchar(12)  NOT NULL DEFAULT 'INCOMPLETO',
    -- % de la masa de los ingredientes con datos nutricionales
    completitud_pct         smallint     NOT NULL DEFAULT 0,
    -- Derivados de los ingredientes
    alergenos_contiene      text[]       NOT NULL DEFAULT '{}',
    alergenos_trazas        text[]       NOT NULL DEFAULT '{}',
    contiene_lactosa        boolean      NOT NULL DEFAULT false,
    apto_vegano             boolean      NOT NULL DEFAULT false,
    apto_vegetariano        boolean      NOT NULL DEFAULT false,
    apto_pescetariano       boolean      NOT NULL DEFAULT false,
    -- true solo si todos los ingredientes están emparejados con alimentos con los alérgenos revisados
    alergenos_verificados   boolean      NOT NULL DEFAULT false,
    visibilidad             varchar(10)  NOT NULL,
    propietario_id          uuid         NULL,
    -- Solo las publicadas por el administrador entran en el planificador
    publicada               boolean      NOT NULL DEFAULT false,
    sincronizado_en         timestamptz  NULL,
    creado_en               timestamptz  NOT NULL,
    actualizado_en          timestamptz  NOT NULL,
    CONSTRAINT pk_receta PRIMARY KEY (id),
    CONSTRAINT uk_receta_origen_referencia UNIQUE (origen, referencia_externa),
    CONSTRAINT fk_receta_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id),
    CONSTRAINT fk_receta_propietario FOREIGN KEY (propietario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_receta_origen CHECK (origen IN ('PROPIA', 'THEMEALDB')),
    -- Las importadas se identifican por su idMeal; las propias no tienen referencia externa
    CONSTRAINT ck_receta_referencia CHECK ((origen = 'THEMEALDB') = (referencia_externa IS NOT NULL)),
    CONSTRAINT ck_receta_raciones CHECK (raciones >= 1),
    CONSTRAINT ck_receta_tiempo CHECK (tiempo_preparacion_min IS NULL OR tiempo_preparacion_min > 0),
    CONSTRAINT ck_receta_dificultad CHECK (dificultad IS NULL OR dificultad IN ('FACIL', 'MEDIA', 'DIFICIL')),
    CONSTRAINT ck_receta_franjas CHECK (franjas_aptas <@ ARRAY[
        'DESAYUNO', 'MEDIA_MANANA', 'COMIDA', 'MERIENDA', 'CENA', 'PRE_ENTRENO', 'POST_ENTRENO']::text[]),
    CONSTRAINT ck_receta_estado_nutricional CHECK (estado_nutricional IN ('COMPLETO', 'APROXIMADO', 'INCOMPLETO')),
    CONSTRAINT ck_receta_completitud CHECK (completitud_pct BETWEEN 0 AND 100),
    CONSTRAINT ck_receta_alergenos_contiene CHECK (alergenos_contiene <@ ARRAY[
        'GLUTEN', 'CRUSTACEOS', 'HUEVOS', 'PESCADO', 'CACAHUETES', 'SOJA', 'LACTEOS',
        'FRUTOS_DE_CASCARA', 'APIO', 'MOSTAZA', 'SESAMO', 'SULFITOS', 'ALTRAMUCES', 'MOLUSCOS']::text[]),
    CONSTRAINT ck_receta_alergenos_trazas CHECK (alergenos_trazas <@ ARRAY[
        'GLUTEN', 'CRUSTACEOS', 'HUEVOS', 'PESCADO', 'CACAHUETES', 'SOJA', 'LACTEOS',
        'FRUTOS_DE_CASCARA', 'APIO', 'MOSTAZA', 'SESAMO', 'SULFITOS', 'ALTRAMUCES', 'MOLUSCOS']::text[]),
    CONSTRAINT ck_receta_visibilidad CHECK (visibilidad IN ('PUBLICO', 'PRIVADO')),
    CONSTRAINT ck_receta_privada_con_propietario CHECK (visibilidad <> 'PRIVADO' OR propietario_id IS NOT NULL)
);

-- Búsqueda tolerante a tildes y errores de escritura, como en alimento (pg_trgm se creó en V1)
CREATE INDEX ix_receta_nombre_normalizado_trgm ON receta USING gin (nombre_normalizado gin_trgm_ops);
CREATE INDEX ix_receta_categoria_id ON receta (categoria_id);
CREATE INDEX ix_receta_propietario_id ON receta (propietario_id);

COMMENT ON TABLE receta IS
    'Recetas propias e importadas de TheMealDB (en inglés, con raciones supuestas). Los nutrientes, los alérgenos y la aptitud para dietas se calculan a partir de los ingredientes emparejados.';

-- ---------------------------------------------------------------------------------------------
-- receta_ingrediente
-- ---------------------------------------------------------------------------------------------
CREATE TABLE receta_ingrediente (
    id                 uuid         NOT NULL,
    receta_id          uuid         NOT NULL,
    -- NULL mientras el ingrediente no está emparejado con un alimento del catálogo
    alimento_id        uuid         NULL,
    -- Texto de TheMealDB tal cual: "Chicken Breast", "200g", "1 tbsp"...
    nombre_original    varchar(100) NOT NULL,
    medida_original    varchar(60)  NULL,
    cantidad_gramos    numeric(7,1) NULL,
    estado_conversion  varchar(12)  NOT NULL DEFAULT 'SIN_DATOS',
    orden              smallint     NOT NULL,
    creado_en          timestamptz  NOT NULL,
    actualizado_en     timestamptz  NOT NULL,
    CONSTRAINT pk_receta_ingrediente PRIMARY KEY (id),
    CONSTRAINT uk_receta_ingrediente_orden UNIQUE (receta_id, orden),
    CONSTRAINT fk_receta_ingrediente_receta FOREIGN KEY (receta_id) REFERENCES receta (id) ON DELETE CASCADE,
    -- Un alimento usado en una receta no se puede borrar (el catálogo lo desactiva). La comprobación se aplaza al
    -- final de la transacción (DEFERRABLE INITIALLY DEFERRED): al borrar una cuenta, la cascada borra sus alimentos
    -- privados antes que sus recetas, y con RESTRICT o NO ACTION inmediatos ese borrado fallaría a medias
    CONSTRAINT fk_receta_ingrediente_alimento FOREIGN KEY (alimento_id) REFERENCES alimento (id)
        ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_receta_ingrediente_gramos CHECK (cantidad_gramos IS NULL OR cantidad_gramos >= 0),
    CONSTRAINT ck_receta_ingrediente_estado
        CHECK (estado_conversion IN ('EXACTA', 'ESTIMADA', 'DESPRECIABLE', 'SIN_DATOS'))
);

-- Para buscar recetas por alimento y para la comprobación de la FK al borrar un alimento
CREATE INDEX ix_receta_ingrediente_alimento_id ON receta_ingrediente (alimento_id);

-- ---------------------------------------------------------------------------------------------
-- receta_favorita
-- ---------------------------------------------------------------------------------------------
CREATE TABLE receta_favorita (
    usuario_id  uuid        NOT NULL,
    receta_id   uuid        NOT NULL,
    creado_en   timestamptz NOT NULL,
    CONSTRAINT pk_receta_favorita PRIMARY KEY (usuario_id, receta_id),
    CONSTRAINT fk_receta_favorita_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_receta_favorita_receta FOREIGN KEY (receta_id) REFERENCES receta (id) ON DELETE CASCADE
);

-- Para el ON DELETE CASCADE al borrar una receta
CREATE INDEX ix_receta_favorita_receta_id ON receta_favorita (receta_id);
