-- V3: perfil deportivo y preferencias alimentarias (§3.B y §6.1 de docs/PROJECT_PROPOSAL.md)

-- ---------------------------------------------------------------------------------------------
-- perfil_deportivo (1:1 con usuario)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE perfil_deportivo (
    id                        uuid         NOT NULL,
    usuario_id                uuid         NOT NULL,
    fecha_nacimiento          date         NOT NULL,
    sexo                      varchar(10)  NOT NULL,
    altura_cm                 numeric(5,1) NOT NULL,
    peso_actual_kg            numeric(5,2) NOT NULL,
    porcentaje_grasa          numeric(4,1) NULL,
    objetivo                  varchar(20)  NOT NULL,
    fecha_competicion         date         NULL,
    -- Código de tipo_actividad; la FK se añade en la migración que crea esa tabla
    deporte_principal         varchar(40)  NOT NULL,
    nivel_actividad           varchar(12)  NOT NULL,
    dias_entrenamiento_semana smallint     NOT NULL,
    duracion_media_min        smallint     NOT NULL,
    comidas_por_dia           smallint     NOT NULL,
    tiempo_max_cocina_min     smallint     NULL,
    objetivo_kcal             numeric(7,1) NULL,
    objetivo_proteina_g       numeric(7,1) NULL,
    objetivo_carbohidratos_g  numeric(7,1) NULL,
    objetivo_grasa_g          numeric(7,1) NULL,
    objetivos_manuales        boolean      NOT NULL DEFAULT false,
    creado_en                 timestamptz  NOT NULL,
    actualizado_en            timestamptz  NOT NULL,
    CONSTRAINT pk_perfil_deportivo PRIMARY KEY (id),
    CONSTRAINT uk_perfil_deportivo_usuario_id UNIQUE (usuario_id),
    CONSTRAINT fk_perfil_deportivo_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_perfil_deportivo_sexo CHECK (sexo IN ('HOMBRE', 'MUJER')),
    CONSTRAINT ck_perfil_deportivo_altura_cm CHECK (altura_cm BETWEEN 120 AND 230),
    CONSTRAINT ck_perfil_deportivo_peso_actual_kg CHECK (peso_actual_kg BETWEEN 30 AND 250),
    CONSTRAINT ck_perfil_deportivo_porcentaje_grasa CHECK (porcentaje_grasa BETWEEN 3 AND 60),
    CONSTRAINT ck_perfil_deportivo_objetivo
        CHECK (objetivo IN ('PERDER_GRASA', 'MANTENER', 'GANAR_MUSCULO', 'RENDIMIENTO', 'COMPETICION')),
    CONSTRAINT ck_perfil_deportivo_nivel_actividad
        CHECK (nivel_actividad IN ('SEDENTARIO', 'LIGERO', 'MODERADO', 'ALTO')),
    CONSTRAINT ck_perfil_deportivo_dias_entrenamiento_semana CHECK (dias_entrenamiento_semana BETWEEN 0 AND 7),
    CONSTRAINT ck_perfil_deportivo_duracion_media_min CHECK (duracion_media_min BETWEEN 0 AND 600),
    CONSTRAINT ck_perfil_deportivo_comidas_por_dia CHECK (comidas_por_dia BETWEEN 3 AND 6)
);

COMMENT ON TABLE perfil_deportivo IS
    'Datos del deportista para calcular sus necesidades (Mifflin-St Jeor) y sus objetivos base sin entrenamiento; si objetivos_manuales = true no se recalculan.';
COMMENT ON COLUMN perfil_deportivo.fecha_competicion IS
    'Obligatoria solo si objetivo = COMPETICION (lo valida el servicio).';

-- ---------------------------------------------------------------------------------------------
-- preferencia_alimentaria (N:1 con usuario)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE preferencia_alimentaria (
    id             uuid        NOT NULL,
    usuario_id     uuid        NOT NULL,
    tipo           varchar(15) NOT NULL,
    alergeno       varchar(20) NULL,
    intolerancia   varchar(10) NULL,
    dieta          varchar(15) NULL,
    -- Las FK a alimento y categoria se añaden en la migración que crea esas tablas
    alimento_id    uuid        NULL,
    categoria_id   uuid        NULL,
    severidad      varchar(12) NOT NULL,
    creado_en      timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT pk_preferencia_alimentaria PRIMARY KEY (id),
    CONSTRAINT fk_preferencia_alimentaria_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_preferencia_alimentaria_tipo
        CHECK (tipo IN ('ALERGIA', 'INTOLERANCIA', 'DIETA', 'RECHAZO', 'FAVORITO')),
    -- Los 14 alérgenos de declaración obligatoria del Reglamento (UE) 1169/2011
    CONSTRAINT ck_preferencia_alimentaria_alergeno
        CHECK (alergeno IN ('GLUTEN', 'CRUSTACEOS', 'HUEVOS', 'PESCADO', 'CACAHUETES', 'SOJA', 'LACTEOS',
                            'FRUTOS_DE_CASCARA', 'APIO', 'MOSTAZA', 'SESAMO', 'SULFITOS', 'ALTRAMUCES', 'MOLUSCOS')),
    CONSTRAINT ck_preferencia_alimentaria_intolerancia CHECK (intolerancia IN ('LACTOSA')),
    CONSTRAINT ck_preferencia_alimentaria_dieta
        CHECK (dieta IN ('VEGETARIANA', 'VEGANA', 'PESCETARIANA', 'HALAL', 'KOSHER')),
    CONSTRAINT ck_preferencia_alimentaria_severidad CHECK (severidad IN ('ESTRICTA', 'PREFERENCIA')),
    CONSTRAINT ck_preferencia_alimentaria_coherencia CHECK (
        (tipo = 'ALERGIA' AND alergeno IS NOT NULL)
        OR (tipo = 'INTOLERANCIA' AND intolerancia IS NOT NULL)
        OR (tipo = 'DIETA' AND dieta IS NOT NULL)
        OR (tipo IN ('RECHAZO', 'FAVORITO') AND (alimento_id IS NOT NULL OR categoria_id IS NOT NULL))
    )
);

CREATE INDEX ix_preferencia_alimentaria_usuario_id ON preferencia_alimentaria (usuario_id);

COMMENT ON TABLE preferencia_alimentaria IS
    'Alergias, intolerancias, dietas y alimentos o categorías rechazados o favoritos. La celiaquía es ALERGIA a GLUTEN; no hay dietas "sin gluten" ni "sin lactosa". Las de severidad ESTRICTA son filtro duro.';
