-- Catálogo de tipos de actividad (§6.1 TipoActividad). Migración repetible: Flyway la vuelve a
-- ejecutar cuando cambia este fichero, después de las versionadas. Los códigos no se cambian nunca
-- (los referencian perfil_deportivo y entrenamiento); para retirar una actividad, activo = false.
-- Al volver a ejecutarse no toca activo: una actividad desactivada sigue desactivada.
--
-- Fuente de los MET: Ainsworth BE et al. "2011 Compendium of Physical Activities: a second update of
-- codes and MET values". Med Sci Sports Exerc. 2011;43(8):1575-81. https://pacompendium.com
-- PENDIENTE DE REVISAR por una persona con formación en nutrición deportiva antes de producción.
-- Dos valores no figuran como tales en el Compendium y son aproximaciones:
--   PADEL    → 6.0, estimado (el Compendium de 2011 no tiene código de pádel).
--   CROSSFIT → 8.0, el de "entrenamiento en circuito, esfuerzo vigoroso".

INSERT INTO tipo_actividad (codigo, nombre, met, categoria, activo)
VALUES
    ('CARRERA_8KMH',            'Carrera a 8 km/h',                   8.3,  'CARRERA',      true),
    ('CARRERA_9_7KMH',          'Carrera a 9,7 km/h',                 9.8,  'CARRERA',      true),
    ('CARRERA_12KMH',           'Carrera a 12 km/h',                  11.8, 'CARRERA',      true),
    ('CICLISMO_16_19KMH',       'Ciclismo a 16-19 km/h',              6.8,  'CICLISMO',     true),
    ('CICLISMO_19_22KMH',       'Ciclismo a 19-22 km/h',              8.0,  'CICLISMO',     true),
    ('NATACION_MODERADA',       'Natación, esfuerzo moderado',        5.8,  'NATACION',     true),
    ('NATACION_INTENSA',        'Natación, esfuerzo intenso',         9.8,  'NATACION',     true),
    ('FUERZA_MODERADA',         'Entrenamiento de fuerza moderado',   3.5,  'FUERZA',       true),
    ('FUERZA_INTENSA',          'Entrenamiento de fuerza intenso',    6.0,  'FUERZA',       true),
    ('CIRCUITO_INTENSO',        'Entrenamiento en circuito intenso',  8.0,  'FUERZA',       true),
    ('CROSSFIT',                'CrossFit',                           8.0,  'FUERZA',       true),
    ('FUTBOL_PARTIDO',          'Fútbol, partido',                    10.0, 'DEPORTE_EQUIPO', true),
    ('BALONCESTO_PARTIDO',      'Baloncesto, partido',                6.5,  'DEPORTE_EQUIPO', true),
    ('TENIS_INDIVIDUAL',        'Tenis individual',                   8.0,  'RAQUETA',      true),
    ('PADEL',                   'Pádel',                              6.0,  'RAQUETA',      true),
    ('REMO_ERGOMETRO_MODERADO', 'Remo en ergómetro, esfuerzo moderado', 7.0, 'CARDIO',      true),
    ('ELIPTICA_MODERADA',       'Elíptica, esfuerzo moderado',        5.0,  'CARDIO',       true),
    ('CAMINAR_5_6KMH',          'Caminar a 5,6 km/h',                 4.3,  'CAMINAR',      true),
    ('SENDERISMO',              'Senderismo',                         6.0,  'CAMINAR',      true),
    ('YOGA',                    'Yoga',                               2.5,  'FLEXIBILIDAD', true)
ON CONFLICT (codigo) DO UPDATE SET
    nombre    = EXCLUDED.nombre,
    met       = EXCLUDED.met,
    categoria = EXCLUDED.categoria;
