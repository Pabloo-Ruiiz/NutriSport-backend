-- V10: auditoría de las acciones de administración (§3.I de docs/PROJECT_PROPOSAL.md)
-- Se escribe en la misma transacción que la acción: si la acción se deshace, su registro también.

CREATE TABLE auditoria (
    id              uuid         NOT NULL,
    -- NULL si el administrador se ha eliminado después: el registro se conserva
    admin_id        uuid         NULL,
    accion          varchar(60)  NOT NULL,
    entidad         varchar(40)  NOT NULL,
    entidad_id      varchar(60)  NULL,
    detalle         jsonb        NULL,
    ip              varchar(45)  NULL,
    creado_en       timestamptz  NOT NULL,
    actualizado_en  timestamptz  NOT NULL,
    CONSTRAINT pk_auditoria PRIMARY KEY (id),
    CONSTRAINT fk_auditoria_admin FOREIGN KEY (admin_id)
        REFERENCES usuario (id) ON DELETE SET NULL
);

CREATE INDEX ix_auditoria_creado_en ON auditoria (creado_en);
-- Para el ON DELETE SET NULL al borrar un administrador (sin él, recorrería toda la tabla)
CREATE INDEX ix_auditoria_admin_id ON auditoria (admin_id);

COMMENT ON TABLE auditoria IS
    'Acciones de administración (quién, qué, sobre qué entidad, desde qué IP y con qué detalle). Solo se inserta, nunca se modifica.';
COMMENT ON COLUMN auditoria.ip IS
    'IP de la petición (hasta 45 caracteres para IPv6).';
