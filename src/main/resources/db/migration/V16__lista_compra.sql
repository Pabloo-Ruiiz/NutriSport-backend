-- V16: listas de la compra y sus productos (Ampliación, §3.E, §6.1 y §7.6 de docs/PROJECT_PROPOSAL.md).
-- La guía la llama V14, pero V14 (menús) y V15 (sugerencias de la IA) ya existen: es la siguiente versión libre.

CREATE TABLE lista_compra (
    id               uuid         NOT NULL,
    usuario_id       uuid         NOT NULL,
    -- Menú del que se generó; NULL si se crea a mano o si se borra el menú (la lista se conserva)
    menu_semanal_id  uuid         NULL,
    nombre           varchar(100) NOT NULL,
    -- Rango de días del menú que cubre; los dos NULL si no sale de un menú
    fecha_desde      date         NULL,
    fecha_hasta      date         NULL,
    estado           varchar(10)  NOT NULL,
    creado_en        timestamptz  NOT NULL,
    actualizado_en   timestamptz  NOT NULL,
    CONSTRAINT pk_lista_compra PRIMARY KEY (id),
    CONSTRAINT fk_lista_compra_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_lista_compra_menu FOREIGN KEY (menu_semanal_id)
        REFERENCES menu_semanal (id) ON DELETE SET NULL,
    CONSTRAINT ck_lista_compra_estado CHECK (estado IN ('ACTIVA', 'COMPLETADA')),
    CONSTRAINT ck_lista_compra_fechas CHECK ((fecha_desde IS NULL) = (fecha_hasta IS NULL)
        AND (fecha_hasta IS NULL OR fecha_hasta >= fecha_desde))
);

-- Listas de un usuario, la más reciente primero
CREATE INDEX ix_lista_compra_usuario_creado ON lista_compra (usuario_id, creado_en);
-- Para el ON DELETE SET NULL al borrar un menú
CREATE INDEX ix_lista_compra_menu_id ON lista_compra (menu_semanal_id);

COMMENT ON TABLE lista_compra IS
    'Lista de la compra de un usuario, generada desde un menú (rango fecha_desde–fecha_hasta) o creada a mano.';

CREATE TABLE producto_compra (
    id               uuid          NOT NULL,
    lista_compra_id  uuid          NOT NULL,
    -- Alimento del catálogo; NULL en los productos libres o si se borra el alimento
    alimento_id      uuid          NULL,
    -- Texto del producto libre; en los de un alimento, copia de su nombre para que el producto siga teniendo
    -- nombre si se borra el alimento
    nombre_libre     varchar(120)  NULL,
    cantidad         numeric(9,2)  NOT NULL,
    unidad           varchar(5)    NOT NULL,
    -- Categoria.seccion_compra del alimento ("Despensa", "Carnes"...) u "Otros"
    seccion          varchar(40)   NOT NULL,
    comprado         boolean       NOT NULL DEFAULT false,
    -- Añadido a mano (no sale del menú)
    manual           boolean       NOT NULL DEFAULT false,
    -- Cantidad por día: [{"fecha": "2026-11-30", "gramos": 200.0}, ...] (gramos o ml según el alimento)
    desglose         jsonb         NOT NULL DEFAULT '[]',
    orden            smallint      NOT NULL,
    creado_en        timestamptz   NOT NULL,
    actualizado_en   timestamptz   NOT NULL,
    CONSTRAINT pk_producto_compra PRIMARY KEY (id),
    CONSTRAINT fk_producto_compra_lista FOREIGN KEY (lista_compra_id)
        REFERENCES lista_compra (id) ON DELETE CASCADE,
    CONSTRAINT fk_producto_compra_alimento FOREIGN KEY (alimento_id) REFERENCES alimento (id) ON DELETE SET NULL,
    CONSTRAINT ck_producto_compra_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_producto_compra_unidad CHECK (unidad IN ('G', 'KG', 'ML', 'L', 'UD')),
    CONSTRAINT ck_producto_compra_nombre CHECK (alimento_id IS NOT NULL OR nombre_libre IS NOT NULL),
    CONSTRAINT ck_producto_compra_desglose CHECK (jsonb_typeof(desglose) = 'array')
);

CREATE INDEX ix_producto_compra_lista ON producto_compra (lista_compra_id, orden);
-- Para el ON DELETE SET NULL al borrar un alimento
CREATE INDEX ix_producto_compra_alimento_id ON producto_compra (alimento_id);

COMMENT ON TABLE producto_compra IS
    'Producto de una lista de la compra: un alimento del catálogo (cantidades sumadas del menú) o un texto libre. comprado = marcado en la tienda; manual = añadido por el usuario.';
