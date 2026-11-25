-- V18: conversaciones con el asistente de IA y sus mensajes (Ampliación, §3.H, §4.4 y §6.1 de docs/PROJECT_PROPOSAL.md).
-- La guía la llama V16, pero V16 (lista de la compra) y V17 (insights) ya existen: es la siguiente versión libre.

CREATE TABLE conversacion_ia (
    id                 uuid          NOT NULL,
    usuario_id         uuid          NOT NULL,
    -- Se rellena con el primer mensaje; NULL hasta entonces
    titulo             varchar(100)  NULL,
    archivada          boolean       NOT NULL DEFAULT false,
    -- NULL mientras no tenga mensajes
    ultimo_mensaje_en  timestamptz   NULL,
    creado_en          timestamptz   NOT NULL,
    actualizado_en     timestamptz   NOT NULL,
    CONSTRAINT pk_conversacion_ia PRIMARY KEY (id),
    CONSTRAINT fk_conversacion_ia_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
);

-- Conversaciones de un usuario, la de actividad más reciente primero
CREATE INDEX ix_conversacion_ia_usuario_ultimo ON conversacion_ia (usuario_id, ultimo_mensaje_en);

COMMENT ON TABLE conversacion_ia IS 'Conversación de un usuario con el asistente de IA (§3.H).';

CREATE TABLE mensaje_ia (
    id                   uuid         NOT NULL,
    conversacion_id      uuid         NOT NULL,
    rol                  varchar(12)  NOT NULL,
    contenido            text         NOT NULL,
    -- Nombres de las herramientas de solo lectura que usó el asistente para responder: ["resumenDiario", ...]
    herramientas_usadas  jsonb        NOT NULL DEFAULT '[]',
    -- Solo en las respuestas del asistente
    tokens_entrada       integer      NULL,
    tokens_salida        integer      NULL,
    -- Valoración del usuario de una respuesta: −1 (no útil) o 1 (útil)
    valoracion           smallint     NULL,
    creado_en            timestamptz  NOT NULL,
    actualizado_en       timestamptz  NOT NULL,
    CONSTRAINT pk_mensaje_ia PRIMARY KEY (id),
    CONSTRAINT fk_mensaje_ia_conversacion FOREIGN KEY (conversacion_id)
        REFERENCES conversacion_ia (id) ON DELETE CASCADE,
    CONSTRAINT ck_mensaje_ia_rol CHECK (rol IN ('USUARIO', 'ASISTENTE', 'HERRAMIENTA')),
    CONSTRAINT ck_mensaje_ia_herramientas CHECK (jsonb_typeof(herramientas_usadas) = 'array'),
    CONSTRAINT ck_mensaje_ia_tokens CHECK ((tokens_entrada IS NULL OR tokens_entrada >= 0)
        AND (tokens_salida IS NULL OR tokens_salida >= 0)),
    CONSTRAINT ck_mensaje_ia_valoracion CHECK (valoracion IN (-1, 1))
);

-- Mensajes de una conversación en orden (historial y últimos N para el contexto del modelo)
CREATE INDEX ix_mensaje_ia_conversacion_creado ON mensaje_ia (conversacion_id, creado_en);

COMMENT ON TABLE mensaje_ia IS
    'Mensaje de una conversación con el asistente. Las cifras del usuario de una respuesta salen de las herramientas de solo lectura registradas en herramientas_usadas, nunca de la memoria del modelo (§4.4).';
