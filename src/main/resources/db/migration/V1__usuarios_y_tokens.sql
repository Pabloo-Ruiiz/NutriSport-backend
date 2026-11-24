-- V1: usuarios, roles y tokens de autenticación (§3.A y §6.1 de docs/PROJECT_PROPOSAL.md)

-- Búsqueda por trigramas en catálogo (alimentos, recetas); se usará en migraciones posteriores
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------------------------------
-- usuario
-- ---------------------------------------------------------------------------------------------
CREATE TABLE usuario (
    id                               uuid         NOT NULL,
    email                            varchar(254) NOT NULL,
    password_hash                    varchar(100) NOT NULL,
    nombre                           varchar(80)  NOT NULL,
    estado                           varchar(12)  NOT NULL,
    email_verificado_en              timestamptz  NULL,
    mayor_edad_declarada_en          timestamptz  NOT NULL,
    consentimiento_salud_en          timestamptz  NOT NULL,
    consentimiento_salud_version     varchar(10)  NOT NULL,
    consentimiento_salud_revocado_en timestamptz  NULL,
    unidades                         varchar(10)  NOT NULL DEFAULT 'METRICO',
    ultimo_acceso                    timestamptz  NULL,
    eliminado_en                     timestamptz  NULL,
    creado_en                        timestamptz  NOT NULL,
    actualizado_en                   timestamptz  NOT NULL,
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT ck_usuario_estado CHECK (estado IN ('ACTIVO', 'BLOQUEADO', 'ELIMINADO')),
    CONSTRAINT ck_usuario_unidades CHECK (unidades IN ('METRICO'))
);

COMMENT ON TABLE usuario IS
    'Cuenta de usuario: credenciales, estado, declaración de mayoría de edad y consentimiento de datos de salud (RGPD art. 9).';

-- ---------------------------------------------------------------------------------------------
-- usuario_rol
-- ---------------------------------------------------------------------------------------------
CREATE TABLE usuario_rol (
    usuario_id uuid        NOT NULL,
    rol        varchar(20) NOT NULL,
    CONSTRAINT pk_usuario_rol PRIMARY KEY (usuario_id, rol),
    CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_usuario_rol_rol CHECK (rol IN ('DEPORTISTA', 'ADMIN'))
);

COMMENT ON TABLE usuario_rol IS
    'Roles de cada usuario; un usuario puede tener DEPORTISTA y ADMIN a la vez.';

-- ---------------------------------------------------------------------------------------------
-- refresh_token
-- ---------------------------------------------------------------------------------------------
CREATE TABLE refresh_token (
    id              uuid        NOT NULL,
    usuario_id      uuid        NOT NULL,
    token_hash      char(64)    NOT NULL,
    familia         uuid        NOT NULL,
    expira_en       timestamptz NOT NULL,
    revocado        boolean     NOT NULL DEFAULT false,
    reemplazado_por uuid        NULL,
    creado_en       timestamptz NOT NULL,
    actualizado_en  timestamptz NOT NULL,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_token_usuario_id ON refresh_token (usuario_id);
CREATE INDEX ix_refresh_token_familia ON refresh_token (familia);

COMMENT ON TABLE refresh_token IS
    'Refresh tokens rotatorios: solo se guarda el SHA-256 en hex; si se reutiliza uno ya rotado se revoca toda su familia (§5.5).';

-- ---------------------------------------------------------------------------------------------
-- token_usuario
-- ---------------------------------------------------------------------------------------------
CREATE TABLE token_usuario (
    id             uuid         NOT NULL,
    usuario_id     uuid         NOT NULL,
    tipo           varchar(30)  NOT NULL,
    token_hash     char(64)     NOT NULL,
    dato_extra     varchar(254) NULL,
    expira_en      timestamptz  NOT NULL,
    usado_en       timestamptz  NULL,
    creado_en      timestamptz  NOT NULL,
    actualizado_en timestamptz  NOT NULL,
    CONSTRAINT pk_token_usuario PRIMARY KEY (id),
    CONSTRAINT uk_token_usuario_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_token_usuario_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_token_usuario_tipo CHECK (tipo IN ('VERIFICACION_EMAIL', 'RECUPERACION_PASSWORD', 'CAMBIO_EMAIL'))
);

CREATE INDEX ix_token_usuario_usuario_id ON token_usuario (usuario_id);

COMMENT ON TABLE token_usuario IS
    'Tokens de un solo uso enviados por email (verificación, recuperación de contraseña, cambio de email); solo se guarda su SHA-256. dato_extra guarda el nuevo email en CAMBIO_EMAIL.';
