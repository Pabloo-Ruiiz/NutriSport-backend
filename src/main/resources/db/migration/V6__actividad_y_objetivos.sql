-- V6: actividad deportiva y objetivo diario (§3.G y §6.1 TipoActividad, Entrenamiento y ObjetivoDiario
-- de docs/PROJECT_PROPOSAL.md). El catálogo de actividades se carga en R__tipos_actividad.sql.

-- ---------------------------------------------------------------------------------------------
-- tipo_actividad (catálogo; la clave es el código)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE tipo_actividad (
    codigo    varchar(40)  NOT NULL,
    nombre    varchar(80)  NOT NULL,
    met       numeric(4,1) NOT NULL,
    categoria varchar(30)  NOT NULL,
    activo    boolean      NOT NULL DEFAULT true,
    CONSTRAINT pk_tipo_actividad PRIMARY KEY (codigo),
    CONSTRAINT ck_tipo_actividad_met CHECK (met > 0)
);

COMMENT ON TABLE tipo_actividad IS
    'Actividades con su equivalente metabólico (MET) del Compendium of Physical Activities, para estimar el gasto de los entrenamientos.';

-- FK pendiente de V3: el deporte principal del perfil es un tipo de actividad
ALTER TABLE perfil_deportivo
    ADD CONSTRAINT fk_perfil_deportivo_deporte_principal
        FOREIGN KEY (deporte_principal) REFERENCES tipo_actividad (codigo);

-- ---------------------------------------------------------------------------------------------
-- entrenamiento
-- ---------------------------------------------------------------------------------------------
CREATE TABLE entrenamiento (
    id                    uuid         NOT NULL,
    usuario_id            uuid         NOT NULL,
    tipo_actividad_codigo varchar(40)  NOT NULL,
    fecha_hora            timestamptz  NOT NULL,
    duracion_min          smallint     NOT NULL,
    intensidad            varchar(5)   NOT NULL,
    rpe                   smallint     NULL,
    estado                varchar(11)  NOT NULL,
    kcal_estimadas        numeric(7,1) NOT NULL,
    distancia_km          numeric(6,2) NULL,
    notas                 varchar(500) NULL,
    creado_en             timestamptz  NOT NULL,
    actualizado_en        timestamptz  NOT NULL,
    CONSTRAINT pk_entrenamiento PRIMARY KEY (id),
    CONSTRAINT fk_entrenamiento_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_entrenamiento_tipo_actividad
        FOREIGN KEY (tipo_actividad_codigo) REFERENCES tipo_actividad (codigo),
    CONSTRAINT ck_entrenamiento_duracion_min CHECK (duracion_min BETWEEN 1 AND 600),
    CONSTRAINT ck_entrenamiento_intensidad CHECK (intensidad IN ('BAJA', 'MEDIA', 'ALTA')),
    CONSTRAINT ck_entrenamiento_rpe CHECK (rpe BETWEEN 1 AND 10),
    CONSTRAINT ck_entrenamiento_estado CHECK (estado IN ('PLANIFICADO', 'REALIZADO', 'OMITIDO')),
    CONSTRAINT ck_entrenamiento_kcal_estimadas CHECK (kcal_estimadas >= 0),
    CONSTRAINT ck_entrenamiento_distancia_km CHECK (distancia_km >= 0)
);

CREATE INDEX ix_entrenamiento_usuario_id_fecha_hora ON entrenamiento (usuario_id, fecha_hora);
CREATE INDEX ix_entrenamiento_tipo_actividad_codigo ON entrenamiento (tipo_actividad_codigo);

COMMENT ON TABLE entrenamiento IS
    'Entrenamientos planificados o realizados. kcal_estimadas es el gasto neto (§3.G); solo PLANIFICADO y REALIZADO cuentan para el objetivo diario.';

-- ---------------------------------------------------------------------------------------------
-- objetivo_diario
-- ---------------------------------------------------------------------------------------------
CREATE TABLE objetivo_diario (
    id                    uuid         NOT NULL,
    usuario_id            uuid         NOT NULL,
    fecha                 date         NOT NULL,
    kcal_base             numeric(7,1) NOT NULL,
    proteina_g            numeric(7,1) NOT NULL,
    carbohidratos_g       numeric(7,1) NOT NULL,
    grasa_g               numeric(7,1) NOT NULL,
    kcal_entreno          numeric(7,1) NOT NULL,
    kcal_total            numeric(7,1) NOT NULL,
    carbohidratos_total_g numeric(7,1) NOT NULL,
    origen                varchar(10)  NOT NULL,
    creado_en             timestamptz  NOT NULL,
    actualizado_en        timestamptz  NOT NULL,
    CONSTRAINT pk_objetivo_diario PRIMARY KEY (id),
    CONSTRAINT uk_objetivo_diario_usuario_id_fecha UNIQUE (usuario_id, fecha),
    CONSTRAINT fk_objetivo_diario_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_objetivo_diario_valores CHECK (kcal_base >= 0 AND proteina_g >= 0 AND carbohidratos_g >= 0
        AND grasa_g >= 0 AND kcal_entreno >= 0 AND kcal_total >= 0 AND carbohidratos_total_g >= 0),
    CONSTRAINT ck_objetivo_diario_origen CHECK (origen IN ('CALCULADO', 'MANUAL'))
);

COMMENT ON TABLE objetivo_diario IS
    'Objetivo de cada día (base del perfil + gasto de los entrenamientos que cuentan), guardado para que el historial y la adherencia no cambien al actualizar el perfil.';
