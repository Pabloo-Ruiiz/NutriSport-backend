-- V7: registro de peso (§3.F y §6.1 RegistroPeso de docs/PROJECT_PROPOSAL.md)
-- Un pesaje por usuario y día. El peso del pesaje más reciente es el peso actual del perfil.

CREATE TABLE registro_peso (
    id             uuid         NOT NULL,
    usuario_id     uuid         NOT NULL,
    fecha          date         NOT NULL,
    peso_kg        numeric(5,2) NOT NULL,
    creado_en      timestamptz  NOT NULL,
    actualizado_en timestamptz  NOT NULL,
    CONSTRAINT pk_registro_peso PRIMARY KEY (id),
    CONSTRAINT uk_registro_peso_usuario_id_fecha UNIQUE (usuario_id, fecha),
    CONSTRAINT fk_registro_peso_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_registro_peso_peso_kg CHECK (peso_kg BETWEEN 30 AND 250)
);

COMMENT ON TABLE registro_peso IS
    'Pesajes del usuario (uno por día) para la evolución del peso con media móvil de 7 días; el más reciente se copia al perfil.';
