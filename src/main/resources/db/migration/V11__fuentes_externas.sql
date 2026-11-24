-- V11: procedencia de los alimentos en proveedores externos (Ampliación, §6.1 de docs/PROJECT_PROPOSAL.md y
-- §6 de docs/API_INTEGRATIONS.md) y datos de producto que aportan esos proveedores.
-- La guía la llama V10, pero V10 ya es la auditoría del MVP: es la siguiente versión libre.

CREATE TABLE alimento_fuente (
    id                  uuid         NOT NULL,
    alimento_id         uuid         NOT NULL,
    proveedor           varchar(20)  NOT NULL,
    -- Código de barras (Open Food Facts), fdcId (USDA) o código BEDCA
    referencia_externa  varchar(40)  NOT NULL,
    -- Ficha del producto en el proveedor, para la atribución
    url                 varchar(300) NULL,
    licencia            varchar(20)  NULL,
    -- Para refrescar los productos de más de 30 días
    sincronizado_en     timestamptz  NOT NULL,
    creado_en           timestamptz  NOT NULL,
    actualizado_en      timestamptz  NOT NULL,
    CONSTRAINT pk_alimento_fuente PRIMARY KEY (id),
    CONSTRAINT fk_alimento_fuente_alimento FOREIGN KEY (alimento_id)
        REFERENCES alimento (id) ON DELETE CASCADE,
    CONSTRAINT uk_alimento_fuente_proveedor_referencia UNIQUE (proveedor, referencia_externa),
    CONSTRAINT ck_alimento_fuente_proveedor CHECK (proveedor IN ('OPEN_FOOD_FACTS', 'USDA', 'BEDCA'))
);

-- Para listar las fuentes de un alimento y para el ON DELETE CASCADE
CREATE INDEX ix_alimento_fuente_alimento_id ON alimento_fuente (alimento_id);

COMMENT ON TABLE alimento_fuente IS
    'Procedencia de un alimento en cada proveedor externo. Un alimento puede tener varias; los de Open Food Facts quedan identificados para poder compartirlos bajo ODbL sin mezclarlos con datos propios.';
COMMENT ON COLUMN alimento_fuente.licencia IS
    'Licencia de los datos del proveedor: ODbL (Open Food Facts), CC0 (USDA)...';

-- Datos de producto de los proveedores. Todos opcionales: NULL = el proveedor no los informa.
ALTER TABLE alimento
    ADD COLUMN ingredientes_texto  text         NULL,
    -- La imagen no se copia: se enlaza la del proveedor con atribución
    ADD COLUMN imagen_url          varchar(300) NULL,
    ADD COLUMN racion_gramos       numeric(7,1) NULL,
    ADD COLUMN racion_descripcion  varchar(60)  NULL,
    ADD CONSTRAINT ck_alimento_racion_gramos CHECK (racion_gramos IS NULL OR racion_gramos > 0);

COMMENT ON COLUMN alimento.racion_gramos IS
    'Ración del envase en gramos (o ml), si el proveedor la informa.';
