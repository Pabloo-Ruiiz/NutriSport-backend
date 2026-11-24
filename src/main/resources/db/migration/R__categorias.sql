-- Categorías iniciales de alimentos y recetas (§6.1). Migración repetible: Flyway la vuelve a
-- ejecutar cada vez que cambia este fichero, siempre después de las versionadas. Los UUID son fijos
-- para que otras migraciones y los datos semilla puedan referenciarlos; nunca se cambian.
--   c0000000-0000-0000-0000-0000000000NN → categorías de ALIMENTO
--   c0000000-0000-0000-0001-0000000000NN → categorías de RECETA

INSERT INTO categoria (id, nombre, tipo, padre_id, seccion_compra, orden, creado_en, actualizado_en)
VALUES
    -- Alimentos
    ('c0000000-0000-0000-0000-000000000001', 'Frutas',                   'ALIMENTO', NULL, 'Frutas y verduras',        1,  now(), now()),
    ('c0000000-0000-0000-0000-000000000002', 'Verduras y hortalizas',    'ALIMENTO', NULL, 'Frutas y verduras',        2,  now(), now()),
    ('c0000000-0000-0000-0000-000000000003', 'Carnes',                   'ALIMENTO', NULL, 'Carnes',                   3,  now(), now()),
    ('c0000000-0000-0000-0000-000000000004', 'Pescados y mariscos',      'ALIMENTO', NULL, 'Pescados y mariscos',      4,  now(), now()),
    ('c0000000-0000-0000-0000-000000000005', 'Huevos',                   'ALIMENTO', NULL, 'Huevos',                   5,  now(), now()),
    ('c0000000-0000-0000-0000-000000000006', 'Lácteos y alternativas',   'ALIMENTO', NULL, 'Lácteos y alternativas',   6,  now(), now()),
    ('c0000000-0000-0000-0000-000000000007', 'Cereales y derivados',     'ALIMENTO', NULL, 'Despensa',                 7,  now(), now()),
    ('c0000000-0000-0000-0000-000000000008', 'Legumbres',                'ALIMENTO', NULL, 'Despensa',                 8,  now(), now()),
    ('c0000000-0000-0000-0000-000000000009', 'Frutos secos',             'ALIMENTO', NULL, 'Despensa',                 9,  now(), now()),
    ('c0000000-0000-0000-0000-000000000010', 'Aceites y grasas',         'ALIMENTO', NULL, 'Despensa',                 10, now(), now()),
    ('c0000000-0000-0000-0000-000000000011', 'Bebidas',                  'ALIMENTO', NULL, 'Bebidas',                  11, now(), now()),
    ('c0000000-0000-0000-0000-000000000012', 'Congelados',               'ALIMENTO', NULL, 'Congelados',               12, now(), now()),
    ('c0000000-0000-0000-0000-000000000013', 'Suplementos y preparados', 'ALIMENTO', NULL, 'Suplementos y preparados', 13, now(), now()),
    -- Destino de los productos de Open Food Facts sin categoría equivalente
    ('c0000000-0000-0000-0000-000000000014', 'Otros',                    'ALIMENTO', NULL, 'Otros',                    14, now(), now()),
    -- Recetas
    ('c0000000-0000-0000-0001-000000000001', 'Desayunos',                'RECETA',   NULL, NULL,                       1,  now(), now()),
    ('c0000000-0000-0000-0001-000000000002', 'Platos principales',       'RECETA',   NULL, NULL,                       2,  now(), now()),
    ('c0000000-0000-0000-0001-000000000003', 'Cenas ligeras',            'RECETA',   NULL, NULL,                       3,  now(), now()),
    ('c0000000-0000-0000-0001-000000000004', 'Snacks',                   'RECETA',   NULL, NULL,                       4,  now(), now()),
    ('c0000000-0000-0000-0001-000000000005', 'Pre-entreno',              'RECETA',   NULL, NULL,                       5,  now(), now()),
    ('c0000000-0000-0000-0001-000000000006', 'Post-entreno',             'RECETA',   NULL, NULL,                       6,  now(), now())
ON CONFLICT (id) DO UPDATE SET
    nombre         = EXCLUDED.nombre,
    tipo           = EXCLUDED.tipo,
    padre_id       = EXCLUDED.padre_id,
    seccion_compra = EXCLUDED.seccion_compra,
    orden          = EXCLUDED.orden,
    actualizado_en = now();
