-- Categorías iniciales de alimentos y recetas (§6.1). Migración repetible: Flyway la vuelve a
-- ejecutar cada vez que cambia este fichero, siempre después de las versionadas. Los UUID son fijos
-- para que otras migraciones y los datos semilla puedan referenciarlos; nunca se cambian.
--   c0000000-0000-0000-0000-0000000000NN → categorías de ALIMENTO
--   c0000000-0000-0000-0001-0000000000NN → categorías de RECETA
-- codigo_externo (V12) es la strCategory equivalente de TheMealDB, solo en las categorías de RECETA.

INSERT INTO categoria (id, nombre, tipo, padre_id, seccion_compra, codigo_externo, orden, creado_en, actualizado_en)
VALUES
    -- Alimentos
    ('c0000000-0000-0000-0000-000000000001', 'Frutas',                   'ALIMENTO', NULL, 'Frutas y verduras',        NULL, 1,  now(), now()),
    ('c0000000-0000-0000-0000-000000000002', 'Verduras y hortalizas',    'ALIMENTO', NULL, 'Frutas y verduras',        NULL, 2,  now(), now()),
    ('c0000000-0000-0000-0000-000000000003', 'Carnes',                   'ALIMENTO', NULL, 'Carnes',                   NULL, 3,  now(), now()),
    ('c0000000-0000-0000-0000-000000000004', 'Pescados y mariscos',      'ALIMENTO', NULL, 'Pescados y mariscos',      NULL, 4,  now(), now()),
    ('c0000000-0000-0000-0000-000000000005', 'Huevos',                   'ALIMENTO', NULL, 'Huevos',                   NULL, 5,  now(), now()),
    ('c0000000-0000-0000-0000-000000000006', 'Lácteos y alternativas',   'ALIMENTO', NULL, 'Lácteos y alternativas',   NULL, 6,  now(), now()),
    ('c0000000-0000-0000-0000-000000000007', 'Cereales y derivados',     'ALIMENTO', NULL, 'Despensa',                 NULL, 7,  now(), now()),
    ('c0000000-0000-0000-0000-000000000008', 'Legumbres',                'ALIMENTO', NULL, 'Despensa',                 NULL, 8,  now(), now()),
    ('c0000000-0000-0000-0000-000000000009', 'Frutos secos',             'ALIMENTO', NULL, 'Despensa',                 NULL, 9,  now(), now()),
    ('c0000000-0000-0000-0000-000000000010', 'Aceites y grasas',         'ALIMENTO', NULL, 'Despensa',                 NULL, 10, now(), now()),
    ('c0000000-0000-0000-0000-000000000011', 'Bebidas',                  'ALIMENTO', NULL, 'Bebidas',                  NULL, 11, now(), now()),
    ('c0000000-0000-0000-0000-000000000012', 'Congelados',               'ALIMENTO', NULL, 'Congelados',               NULL, 12, now(), now()),
    ('c0000000-0000-0000-0000-000000000013', 'Suplementos y preparados', 'ALIMENTO', NULL, 'Suplementos y preparados', NULL, 13, now(), now()),
    -- Destino de los productos de Open Food Facts sin categoría equivalente
    ('c0000000-0000-0000-0000-000000000014', 'Otros',                    'ALIMENTO', NULL, 'Otros',                    NULL, 14, now(), now()),
    -- Recetas: por momento del día o del entrenamiento (propias)...
    ('c0000000-0000-0000-0001-000000000001', 'Desayunos',                'RECETA',   NULL, NULL, 'Breakfast',     1,  now(), now()),
    ('c0000000-0000-0000-0001-000000000002', 'Platos principales',       'RECETA',   NULL, NULL, NULL,            2,  now(), now()),
    ('c0000000-0000-0000-0001-000000000003', 'Cenas ligeras',            'RECETA',   NULL, NULL, NULL,            3,  now(), now()),
    ('c0000000-0000-0000-0001-000000000004', 'Snacks',                   'RECETA',   NULL, NULL, NULL,            4,  now(), now()),
    ('c0000000-0000-0000-0001-000000000005', 'Pre-entreno',              'RECETA',   NULL, NULL, NULL,            5,  now(), now()),
    ('c0000000-0000-0000-0001-000000000006', 'Post-entreno',             'RECETA',   NULL, NULL, NULL,            6,  now(), now()),
    -- ...y equivalentes de las categorías de TheMealDB (Breakfast es "Desayunos", arriba)
    ('c0000000-0000-0000-0001-000000000007', 'Pollo',                    'RECETA',   NULL, NULL, 'Chicken',       7,  now(), now()),
    ('c0000000-0000-0000-0001-000000000008', 'Ternera',                  'RECETA',   NULL, NULL, 'Beef',          8,  now(), now()),
    ('c0000000-0000-0000-0001-000000000009', 'Pescado y marisco',        'RECETA',   NULL, NULL, 'Seafood',       9,  now(), now()),
    ('c0000000-0000-0000-0001-000000000010', 'Vegetariana',              'RECETA',   NULL, NULL, 'Vegetarian',    10, now(), now()),
    ('c0000000-0000-0000-0001-000000000011', 'Vegana',                   'RECETA',   NULL, NULL, 'Vegan',         11, now(), now()),
    ('c0000000-0000-0000-0001-000000000012', 'Pasta',                    'RECETA',   NULL, NULL, 'Pasta',         12, now(), now()),
    ('c0000000-0000-0000-0001-000000000013', 'Entrantes',                'RECETA',   NULL, NULL, 'Starter',       13, now(), now()),
    ('c0000000-0000-0000-0001-000000000014', 'Acompañamientos',          'RECETA',   NULL, NULL, 'Side',          14, now(), now()),
    ('c0000000-0000-0000-0001-000000000015', 'Postres',                  'RECETA',   NULL, NULL, 'Dessert',       15, now(), now()),
    ('c0000000-0000-0000-0001-000000000016', 'Cordero',                  'RECETA',   NULL, NULL, 'Lamb',          16, now(), now()),
    ('c0000000-0000-0000-0001-000000000017', 'Cerdo',                    'RECETA',   NULL, NULL, 'Pork',          17, now(), now()),
    ('c0000000-0000-0000-0001-000000000018', 'Cabra',                    'RECETA',   NULL, NULL, 'Goat',          18, now(), now()),
    -- Destino de las recetas de TheMealDB sin categoría equivalente
    ('c0000000-0000-0000-0001-000000000019', 'Otras',                    'RECETA',   NULL, NULL, 'Miscellaneous', 19, now(), now())
ON CONFLICT (id) DO UPDATE SET
    nombre         = EXCLUDED.nombre,
    tipo           = EXCLUDED.tipo,
    padre_id       = EXCLUDED.padre_id,
    seccion_compra = EXCLUDED.seccion_compra,
    codigo_externo = EXCLUDED.codigo_externo,
    orden          = EXCLUDED.orden,
    actualizado_en = now();
