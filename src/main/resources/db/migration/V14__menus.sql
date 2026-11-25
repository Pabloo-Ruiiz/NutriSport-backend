-- V14: menús del planificador y sus comidas planificadas (Ampliación, §3.D y §6.1 de docs/PROJECT_PROPOSAL.md).
-- La guía la llama V13, pero V13 ya es el emparejamiento de ingredientes: es la siguiente versión libre.

CREATE TABLE menu_semanal (
    id                       uuid         NOT NULL,
    usuario_id               uuid         NOT NULL,
    nombre                   varchar(100) NOT NULL,
    tipo                     varchar(8)   NOT NULL,
    fecha_inicio             date         NOT NULL,
    fecha_fin                date         NOT NULL,
    favorito                 boolean      NOT NULL DEFAULT false,
    estado                   varchar(10)  NOT NULL,
    -- Generado por el algoritmo de reglas de §4.5 (no a mano)
    generado_automaticamente boolean      NOT NULL DEFAULT false,
    -- Explicación opcional del menú (LLM)
    explicacion              text         NULL,
    creado_en                timestamptz  NOT NULL,
    actualizado_en           timestamptz  NOT NULL,
    CONSTRAINT pk_menu_semanal PRIMARY KEY (id),
    CONSTRAINT fk_menu_semanal_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_menu_semanal_tipo CHECK (tipo IN ('DIARIO', 'SEMANAL')),
    CONSTRAINT ck_menu_semanal_estado CHECK (estado IN ('BORRADOR', 'ACTIVO', 'ARCHIVADO')),
    CONSTRAINT ck_menu_semanal_fechas CHECK (fecha_fin >= fecha_inicio)
);

-- Menús de un usuario por fecha (planificador, "esta semana")
CREATE INDEX ix_menu_semanal_usuario_fecha ON menu_semanal (usuario_id, fecha_inicio);

COMMENT ON TABLE menu_semanal IS
    'Menú diario o semanal de un usuario. La fecha de cada comida planificada debe estar entre fecha_inicio y fecha_fin (lo comprueba la aplicación).';

CREATE TABLE comida_planificada (
    id                  uuid          NOT NULL,
    menu_semanal_id     uuid          NOT NULL,
    fecha               date          NOT NULL,
    tipo_comida         varchar(15)   NOT NULL,
    receta_id           uuid          NOT NULL,
    raciones            numeric(4,2)  NOT NULL,
    -- No cambia al regenerar el menú
    bloqueada           boolean       NOT NULL DEFAULT false,
    consumida           boolean       NOT NULL DEFAULT false,
    -- La comida registrada al marcarla como consumida; NULL si se borra ese registro
    registro_comida_id  uuid          NULL,
    orden               smallint      NOT NULL,
    creado_en           timestamptz   NOT NULL,
    actualizado_en      timestamptz   NOT NULL,
    CONSTRAINT pk_comida_planificada PRIMARY KEY (id),
    CONSTRAINT fk_comida_planificada_menu FOREIGN KEY (menu_semanal_id)
        REFERENCES menu_semanal (id) ON DELETE CASCADE,
    -- Una receta planificada no se puede borrar. NO ACTION aplazado y no RESTRICT: al borrar una cuenta, la cascada
    -- borra sus recetas privadas antes que sus menús, y con RESTRICT o NO ACTION inmediatos ese borrado fallaría a
    -- medias (el mismo caso que receta_ingrediente en V12)
    CONSTRAINT fk_comida_planificada_receta FOREIGN KEY (receta_id) REFERENCES receta (id)
        ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT fk_comida_planificada_registro FOREIGN KEY (registro_comida_id)
        REFERENCES registro_comida (id) ON DELETE SET NULL,
    CONSTRAINT ck_comida_planificada_tipo_comida CHECK (tipo_comida IN
        ('DESAYUNO', 'MEDIA_MANANA', 'COMIDA', 'MERIENDA', 'CENA', 'PRE_ENTRENO', 'POST_ENTRENO', 'OTRO')),
    CONSTRAINT ck_comida_planificada_raciones CHECK (raciones > 0 AND raciones <= 10),
    -- Solo una comida registrada puede estar enlazada; una consumida puede no tenerla si se borró su registro
    CONSTRAINT ck_comida_planificada_registro CHECK (registro_comida_id IS NULL OR consumida)
);

CREATE INDEX ix_comida_planificada_menu ON comida_planificada (menu_semanal_id, fecha, orden);
-- Para la FK al borrar una receta y para saber si una receta está planificada
CREATE INDEX ix_comida_planificada_receta_id ON comida_planificada (receta_id);
-- Para el ON DELETE SET NULL al borrar una comida registrada
CREATE INDEX ix_comida_planificada_registro_id ON comida_planificada (registro_comida_id);

COMMENT ON TABLE comida_planificada IS
    'Comida de un menú: una receta con sus raciones en una fecha y franja. bloqueada = no cambia al regenerar; consumida = el usuario la ha comido (y puede tener su registro_comida).';
