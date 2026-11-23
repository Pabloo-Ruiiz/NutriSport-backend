# NutriSport AI — Auditoría de la propuesta (Agente 1 · Project Reviewer)

| Campo | Valor |
|---|---|
| Documento auditado | `docs/PROJECT_PROPOSAL.md` v1.0 (1.472 líneas) |
| Contexto consultado | `docs/DESIGN.md` §7 y §9 · `docs/Guia_Github_Backend_NutriSport.md` (solo resumen de pasos y Annex B) |
| Fecha | 21/11/2026 |
| Alcance | Solo lectura. No se ha modificado ningún documento del proyecto |

---

## 1. Resumen general

La propuesta es sólida como especificación de un producto real: la visión está bien argumentada, la arquitectura (monolito modular, puertos/adaptadores para IA) es correcta, la seguridad JWT está bien pensada y el principio "los nutrientes salen del catálogo, no del LLM" es la mejor decisión del documento. El problema principal es otro: **está escrita para un equipo y un producto comercial, no para el proyecto individual de 2.º DAW de 200–400 h que es en realidad**. El MVP declarado (§11.1) ya supera ese presupuesto, la planificación (§11.2, 21 semanas) no da horas, y la guía de backend derivada de ella necesita 331 clases de producción solo en el servidor.

Hay otros tres bloques de problemas: (1) el **modelo de datos** no soporta varias funcionalidades descritas: hay entidades que faltan, no existe histórico de objetivos ni entrenamientos planificados, y la Annex B de la guía tuvo que añadir 5 tablas y 6 columnas; (2) el **tratamiento de datos de salud** contradice su propio principio de minimización (el asistente envía datos de salud al LLM) y prevé pilotos con usuarios reales sin las garantías que exige; (3) **la API** no define DTO ni códigos de error, y le faltan endpoints que piden las pantallas de `DESIGN.md`.

Con un recorte de alcance explícito y unas 10 correcciones concretas, que se listan en §5, el documento quedaría listo para empezar a desarrollar y sería defendible ante un tribunal.

---

## 2. Puntuación

**Puntuación global: 6,5 / 10.** Como especificación técnica vale un 8. Como plan ejecutable por un estudiante, un 4.

| Apartado | Nota | Justificación |
|---|---|---|
| A. Definición del proyecto | 7,5 | Problema, público y propuesta de valor claros y bien acotados (exclusión de menores y TCA). Faltan el contexto académico y la separación entre visión de producto y alcance entregable |
| B. Funcionalidades | 7 | Los módulos A–I están bien delimitados, con flujos e interacciones. Hay flujos con huecos (entrenamientos planificados, consentimiento revocable, cola de validación) y funciones de poco valor para el MVP |
| C. Inteligencia artificial | 7 | Muy buena tabla "qué es IA y qué no" y buenas salvaguardas. Pero el motor de menús por reglas se presenta como IA, el principio 4.1.2 choca con el OCR por LLM y no se trata el estado crudo/cocinado al emparejar |
| D. Arquitectura | 7,5 | Las decisiones de base son correctas y están justificadas (§12.5), y se descartan bien Redis, Kafka y Kubernetes. Sobran piezas para este alcance (ngrx/signals, i18n, PWA, subida prefirmada, SSE de análisis) |
| E. Base de datos | 5,5 | Las entidades núcleo son razonables y la instantánea nutricional está bien pensada. El diagrama ER omite 6 entidades y contradice §6.1 en `PreferenciaAlimentaria`. Faltan catálogo de actividades, *insights*, histórico de objetivos, franja de receta y la FK análisis→alimento |
| F. API REST | 6 | Convenciones REST, Problem Details, paginación y el 404 por propiedad están bien. No hay DTO ni catálogo de errores, y faltan unos 10 endpoints que piden las pantallas. GET con efectos laterales y SSE incompatible con el JWT en cabecera |
| G. Seguridad y privacidad | 7 | Diseño JWT/refresh muy bueno y fotos bien protegidas. La minimización hacia la IA es contradictoria, el consentimiento no tiene ciclo de vida, el bloqueo por intentos facilita una DoS y la edad no se comprueba en el registro |
| H. Viabilidad | 4 | El riesgo "alcance excesivo" se reconoce (§12.3) pero no se mitiga: no hay horas, el MVP incluye foto IA + admin + auth completa, el planificador depende de 300 recetas sin fuente definida y los costes no contemplan el desarrollo |

---

## 3. Problemas detectados

### 3.1. Tabla resumen

| ID | Apdo. | Título | Gravedad |
|---|---|---|---|
| P-01 | H | Alcance y planificación incompatibles con un proyecto individual de 200–400 h | **Crítico** |
| P-02 | G | Datos de salud: la minimización hacia la IA es contradictoria y hay pilotos con usuarios reales sin garantías operativas | **Crítico** |
| P-03 | E | El diagrama ER y §6.1 están incompletos e incoherentes entre sí | Alto |
| P-04 | E/B | No hay histórico de objetivos diarios ni concepto de entrenamiento planificado | Alto |
| P-05 | C/E | El filtro duro de alergias y dietas no está respaldado por el modelo ni por los datos | Alto |
| P-06 | H/E | Catálogo inicial (alimentos y 300 recetas) sin origen, licencia ni idioma resueltos | Alto |
| P-07 | C | El emparejamiento foto→catálogo ignora el estado crudo/cocinado y la preparación | Alto |
| P-08 | F | Faltan endpoints para funcionalidades y pantallas descritas | Alto |
| P-09 | D/F | Flujo de subida de imágenes ambiguo (multipart vs. URL prefirmada) y formatos poco viables | Alto |
| P-10 | F | Contrato de API incompleto: sin DTO ni catálogo de códigos de error | Medio |
| P-11 | C | Clasificación de IA inflada y el principio 4.1.2 contradice el OCR por LLM | Medio |
| P-12 | C/A | Funciones LLM de poco valor que añaden complejidad al MVP | Medio |
| P-13 | B | Numeración de fases incoherente entre §4, §5 y §11; estado de los *insights* contradictorio | Medio |
| P-14 | B | El gasto por MET se cuenta dos veces con el gasto basal | Medio |
| P-15 | G/E | Estados de cuenta, verificación y bloqueo temporal contradictorios o sin modelar | Medio |
| P-16 | D/F | SSE con JWT en cabecera: incompatible con `EventSource` y con `POST` | Medio |
| P-17 | D | Tecnologías sobredimensionadas para el alcance | Medio |
| P-18 | H | RNF y objetivos de éxito no verificables en un proyecto académico | Medio |
| P-19 | E/D | Recetas sin franja de comida y lista de la compra que solo suma gramos | Medio |
| P-20 | G | La mayoría de edad solo se comprueba en el perfil, después de consentir | Medio |
| P-21 | H | Costes: proveedor sin elegir y sin coste de desarrollo ni modo simulado | Medio |
| P-22 | B | Trazabilidad RF ↔ HU incompleta | Bajo |
| P-23 | F | `GET /alimentos/codigo-barras` con efectos laterales y licencia ODbL | Bajo |
| P-24 | E | Enumerados y rangos incoherentes | Bajo |
| P-25 | E | `unaccent` en índice y UUID v7: detalles técnicos no viables tal cual | Bajo |
| P-26 | G | `logout` exige access token; el "Mantener sesión" del diseño no tiene reflejo | Bajo |
| P-27 | D | Análisis huérfanos si el backend se reinicia durante un `@Async` | Bajo |

**Recuento:** 2 críticos · 7 altos · 12 medios · 6 bajos.

---

### 3.2. Detalle

#### P-01 · Alcance y planificación incompatibles con un proyecto individual de 200–400 h — **Crítico**

- **Dónde:** §5.1 l. 398 ("equipo pequeño"); §11.1 l. 1354; §11.2 l. 1362-1367; §11.3 l. 1377-1383; §12.3 l. 1432.
- **Descripción:** el documento no menciona en ningún momento que es un proyecto individual de 2.º DAW. La planificación suma 21 semanas (2+4+4+4+4+3, del 11/01 al ~07/06/2027) sin horas por fase ni dedicación semanal. El "MVP imprescindible" ya incluye autenticación completa con refresh rotatorio y detección de reutilización, verificación por email, perfil con cálculo, catálogo importado, registro manual, registro por foto con IA asíncrona y almacenamiento de objetos, entrenamientos, dashboard, peso, administración de 4 áreas y Docker, todo en backend **y** frontend Angular (≈ 10 pantallas). La Fase 2 exige "pruebas con 10–20 usuarios reales". Como referencia, la guía derivada cifra solo el backend en **331 clases de producción y 51 de test** (Guía, l. 172), sin contar el frontend. El riesgo "Alcance excesivo para el equipo" aparece en §12.3 con probabilidad alta, pero la mitigación ("MVP estricto") no se aplica en el propio documento.
- **Por qué importa:** sin un alcance entregable cerrado, el estudiante llegará a la fecha de entrega con muchos módulos a medias en lugar de un MVP pulido. Además, el tribunal evalúa si lo prometido se corresponde con lo entregado.
- **Propuesta:**
  1. Añadir en §1 un apartado **"1.7. Contexto y alcance del proyecto académico"**. Redacción sugerida: *"NutriSport AI se desarrolla como proyecto final individual de 2.º DAW con una dedicación estimada de N horas (≈ X h/semana durante Y semanas). Este documento describe la visión completa del producto; el alcance comprometido para la entrega es el MVP académico de §11.1. El resto se documenta como evolución."*
  2. Redefinir §11.1 en tres niveles: **MVP de entrega** (≈ 300 h), **Ampliación si sobra tiempo** y **Visión de producto**. Propuesta de MVP de entrega: autenticación (registro, verificación, login JWT + refresh, recuperación), perfil y objetivos, catálogo semilla y búsqueda, registro manual e historial, **registro por foto con edición** (la pieza diferencial), entrenamientos MET, dashboard diario y peso, administración mínima del catálogo y Docker Compose. Como ampliación: menú semanal por reglas con un catálogo pequeño, lista de la compra y asistente de solo lectura sin *streaming*. A visión de producto: OCR, código de barras (o ampliación), incidencias, métricas de coste, PWA, i18n, *insights*, SSE y validación de alimentos de usuario.
  3. Sustituir la duración en semanas de §11.2 por **horas estimadas por fase** con un colchón del 20 % para la memoria, las pruebas y la defensa. Estimación orientativa del MVP: fundamentos 25 h · auth 40 h · perfil 25 h · catálogo 35 h · registro manual 35 h · foto IA 50 h · actividad 15 h · dashboard y peso 30 h · admin 25 h · Docker, tests y documentación 50 h ≈ **330 h**.
  4. Sustituir "pruebas con 10–20 usuarios reales" por "demostración con usuarios de prueba y datos sintéticos" (ver P-02).

#### P-02 · Datos de salud: la minimización hacia la IA es contradictoria y hay pilotos con usuarios reales sin garantías operativas — **Crítico**

- **Dónde:** §4.1 principio 5 (l. 292); §4.4 l. 339 y l. 347; §8.3 l. 1126-1132; §11.2 l. 1364; §6.1 `Usuario.consentimientoSaludEn` (l. 592); `DESIGN.md` §7.16 ("consentimiento de datos de salud (revocable…)").
- **Descripción:**
  1. §8.3 l. 1130 afirma que a los proveedores de IA "solo se envían imagen, edad aproximada, objetivo y restricciones". Sin embargo, el asistente (§4.4) envía al LLM el resultado de herramientas como `getComidas`, `getResumenDiario` o `getEntrenamientos`, y ese resultado es historial alimentario, peso, alergias y entrenamientos, es decir, datos de salud del art. 9 RGPD. La afirmación es falsa tal como está escrita.
  2. El consentimiento es "revocable" (l. 1126 y DESIGN §7.16), pero no se define qué pasa al revocarlo: no hay endpoint, ni campo de revocación, ni versión del texto aceptado, ni comportamiento de la app (que deja de poder tratar peso, comidas y alergias).
  3. La Fase 2 prevé pilotos con usuarios reales mientras que la EIPD, los DPA y el registro de actividades se dejan "antes de la puesta en producción" (l. 1132). Un piloto con personas reales que suben fotos de comida y alergias a un LLM externo **ya es** tratamiento de datos de salud.
  4. No se elige proveedor de IA, así que no se puede afirmar que procese en la UE o que no use los datos para entrenar (l. 1129).
- **Por qué importa:** es el principal riesgo legal del proyecto (el propio §12.3 lo califica de impacto "muy alto"), y el documento contiene una afirmación de privacidad incorrecta.
- **Propuesta:**
  - Reescribir §8.3 l. 1130: *"Seudonimización: nunca se envían nombre, email ni identificadores. **El asistente sí envía al proveedor datos de salud agregados del usuario** (comidas, peso, entrenamientos, restricciones) necesarios para responder; esto se informa en el consentimiento y solo se usa con proveedores con DPA, sin retención para entrenamiento."*
  - Añadir a `Usuario` los campos `consentimientoSaludVersion` y `consentimientoSaludRevocadoEn`, el endpoint `DELETE /cuenta/consentimiento-salud` y la regla de negocio: *"Al revocar, se bloquean las funciones que tratan datos de salud y se ofrece exportar y eliminar la cuenta."*
  - Añadir en §8 un párrafo "Ámbito académico": *"Durante el proyecto solo se usan cuentas de prueba con datos ficticios o del propio autor; no se hacen pilotos con terceros sin un consentimiento informado específico."*
  - Indicar el proveedor elegido (o los candidatos) con una columna "región / política de retención (a verificar)".

#### P-03 · El diagrama ER y §6.1 están incompletos e incoherentes entre sí — **Alto**

- **Dónde:** §6.1 l. 579-756; §6.2 l. 760-943; Annex B de la guía, punto 10.
- **Revisión relación por relación del `erDiagram` contra §6.1:**

| Relación (§6.2) | Valoración |
|---|---|
| `USUARIO ||--o| PERFIL_DEPORTIVO` | Correcta (1:0..1) |
| `USUARIO ||--o{ PREFERENCIA_ALIMENTARIA` | Cardinalidad correcta, pero los atributos del diagrama (`tipo`, `valor`, `severidad`, l. 817-823) **contradicen** §6.1 (`alergeno`, `dieta`, `alimentoId`/`categoriaId`, l. 618-620). Faltan además las FK opcionales a `ALIMENTO` y `CATEGORIA` |
| `USUARIO ||--o{ REGISTRO_COMIDA / ANALISIS_IMAGEN / ENTRENAMIENTO / MENU_SEMANAL / LISTA_COMPRA / CONVERSACION_IA / REGISTRO_PESO / INCIDENCIA` | Correctas. `INCIDENCIA` no recoge `asignadoA` (FK a un usuario admin, l. 754) ni su relación con el recurso afectado (`entidad`/`entidadId` polimórfico, sin FK) |
| `USUARIO ||--o{ ALIMENTO` y `||--o{ RECETA` "crea (privados)" | La relación debe ser **opcional** en el lado de usuario (`|o--o{`), porque los públicos no tienen propietario. Además, `ALIMENTO` y `RECETA` no muestran `propietarioId FK` (l. 824-843) |
| `CATEGORIA ||--o{ ALIMENTO / RECETA` | Correctas. Hay que decidir si `categoriaId` es obligatoria para los productos importados de Open Food Facts |
| `CATEGORIA |o--o{ CATEGORIA` | Correcta |
| `RECETA ||--|{ RECETA_INGREDIENTE` / `ALIMENTO ||--o{ RECETA_INGREDIENTE` | Correctas. `RECETA_INGREDIENTE` no tiene PK en el diagrama, cuando la convención de l. 581 dice que todas las tablas tienen `id` |
| `MENU_SEMANAL ||--|{ COMIDA_PLANIFICADA` | Aceptable. `COMIDA_PLANIFICADA` no muestra `registroComidaId` (l. 676) en sus atributos |
| `RECETA ||--o{ COMIDA_PLANIFICADA` | Correcta |
| `COMIDA_PLANIFICADA |o--o| REGISTRO_COMIDA` | Correcta (FK en `comida_planificada`) |
| `REGISTRO_COMIDA ||--|{ ALIMENTO_REGISTRADO` / `ALIMENTO ||--o{ ALIMENTO_REGISTRADO` | Correctas, pero `DELETE /alimentos/{id}` (§7.3 l. 998) romperá la FK de los registros históricos. Hace falta definir `ON DELETE RESTRICT` y borrado lógico del alimento |
| `ANALISIS_IMAGEN |o--o| REGISTRO_COMIDA` | Correcta para el tipo `COMIDA`. **Falta** `ANALISIS_IMAGEN |o--o| ALIMENTO` para el tipo `ETIQUETA`, cuya confirmación crea un `Alimento` (§7.4 l. 1020). La guía tuvo que añadir un endpoint propio (Annex B 7) |
| `MENU_SEMANAL |o--o{ LISTA_COMPRA` / `LISTA_COMPRA ||--o{ PRODUCTO_COMPRA` / `ALIMENTO |o--o{ PRODUCTO_COMPRA` | Correctas |
| `CONVERSACION_IA ||--o{ MENSAJE_IA` | Correcta |

- **Entidades que faltan o no aparecen en el diagrama:**
  - **`TipoActividad`** (catálogo con MET): §3.G l. 248, §7.8 `/entrenamientos/tipos` y `PerfilDeportivo.deportePrincipal` (l. 606) lo mencionan, pero `Entrenamiento.tipoActividad` es un `varchar` (l. 718). La guía añadió `tipo_actividad`.
  - **Deportes secundarios** (§3.B l. 162): sin modelar.
  - **`Insight`**: §4.6 l. 368 los "genera por usuario" y §7.7 los expone, pero no hay entidad. La guía añadió `insight`.
  - **Roles múltiples**: §2.2 l. 126 dice que un usuario puede tener ambos roles, pero `Usuario.rol` es un enum único (l. 589). La guía añadió `usuario_rol`.
  - `RefreshToken`, `TokenUsuario`, `UsoIA`, `Auditoria` (l. 752-756) no aparecen en el ER.
  - `ProductoCompra.categoriaCompra` es un `varchar` (l. 733) aunque existe `Categoria` con `tipo = COMPRA` (l. 750): la información está duplicada. La guía resolvió con `categoria.seccion_compra`.
- **Por qué importa:** el ER es lo primero que se traduce a migraciones Flyway. Cada hueco obliga a tomar decisiones sobre la marcha, y la guía tuvo que tomar 10 decisiones de modelado que no estaban en la propuesta.
- **Propuesta:** completar el ER con todas las entidades (incluidas las de soporte, aunque sea en un segundo diagrama "Seguridad y soporte"); unificar `PreferenciaAlimentaria` con los atributos de §6.1; añadir `TipoActividad`, `Insight`, `UsuarioRol` (o decidir rol único y borrar l. 126), la FK `AnalisisImagen.alimentoId`, `propietarioId` en `Alimento`/`Receta` y el PK de `RecetaIngrediente`; sustituir `categoriaCompra` por una FK; añadir una regla "los alimentos referenciados no se borran físicamente (`activo = false`)".

#### P-04 · No hay histórico de objetivos diarios ni concepto de entrenamiento planificado — **Alto**

- **Dónde:** §6.1 `PerfilDeportivo` l. 611-612 y `Entrenamiento` l. 713-724; RF-29 (l. 1196); §3.F l. 238 (adherencia); §3.D l. 208 y §4.5 l. 355 ("entrenamientos planificados"); HU-12 l. 1302; HU-10 l. 1292 ("próximo entrenamiento"); Annex B 4.
- **Descripción:**
  1. Los objetivos solo existen en el perfil actual. La adherencia histórica ("% del objetivo cumplido", "días que cumplen objetivo") exige saber cuál era el objetivo **de cada día**. La guía tuvo que recalcularlos con el perfil actual (Annex B 4), de modo que la adherencia de hace dos meses cambia cada vez que el usuario actualiza su peso.
  2. El planificador usa "carga de entrenamiento planificada" y HU-12 exige más carbohidratos los días de "entrenamiento intenso planificado", pero `Entrenamiento` no distingue entre planificado y realizado. Un entrenamiento futuro sumaría gasto al objetivo de ese día antes de hacerse.
- **Por qué importa:** afecta a la exactitud de las estadísticas (F), a la generación de menús (D) y al dashboard. Corregirlo con datos ya en producción obliga a migrar.
- **Propuesta:** añadir `Entrenamiento.estado` (`PLANIFICADO`, `REALIZADO`). Solo los `REALIZADO` ajustan el objetivo consumido, y los `PLANIFICADO` alimentan el planificador y el "próximo entrenamiento". Añadir una entidad ligera `ObjetivoDiario (usuarioId, fecha, kcal, proteina, carbohidratos, grasa, origen CALCULADO|MANUAL)` que se guarda (o se congela al cerrar el día) cuando hay registros. Si se descarta por alcance, documentar explícitamente la limitación en §12.4.

#### P-05 · El filtro duro de alergias y dietas no está respaldado por el modelo ni por los datos — **Alto**

- **Dónde:** §3.B l. 166; §4.2 l. 307; §6.1 `Alimento.alergenos text[]`, `aptoVegano`, `aptoVegetariano` (l. 632-633); `PreferenciaAlimentaria.dieta` (l. 619); HU-05 l. 1263; §4.8; Annex B 13.
- **Descripción:**
  - Con solo `aptoVegano`/`aptoVegetariano` no se pueden aplicar como filtro duro las dietas `HALAL`, `KOSHER` y `PESCETARIANA`. La guía tuvo que resolverlo con etiquetas de receta (Annex B 13).
  - `SIN_GLUTEN` y `SIN_LACTOSA` son a la vez dietas y alérgenos/intolerancias (redundancia).
  - No se distingue entre "contiene" y "puede contener trazas", algo relevante para alergias severas.
  - Las bases genéricas (BEDCA, USDA) **no incluyen, según mi conocimiento, declaración de alérgenos UE (a verificar)**, así que los alérgenos de los alimentos genéricos habría que asignarlos a mano. El documento no lo dice, y la promesa de HU-05 ("ningún menú contiene gluten") depende por completo de esa asignación.
- **Por qué importa:** es la única parte del sistema donde un error puede causar daño físico, y el documento presenta el filtro como garantía ("filtro duro", "100 % en tests").
- **Propuesta:** en `Alimento`, sustituir `alergenos text[]` por `alergenosContiene` y `alergenosTrazas` (enum de los 14 alérgenos) y añadir `aptoPescetariano` (o una tabla `AlimentoDieta`). Eliminar `SIN_GLUTEN`/`SIN_LACTOSA` del enum de dietas y expresarlos como alergia o intolerancia. Excluir `HALAL`/`KOSHER` del MVP o tratarlos explícitamente como preferencia blanda. Añadir en §8.6: *"Los alérgenos de alimentos genéricos se asignan manualmente por el administrador; un alimento sin alérgenos revisados (`alergenosRevisados = false`) se excluye de los menús de usuarios con alergias estrictas."*

#### P-06 · Catálogo inicial (alimentos y 300 recetas) sin origen, licencia ni idioma resueltos — **Alto**

- **Dónde:** §4.8 l. 384-389; §11.1 l. 1354 ("catálogo base BEDCA/USDA"); §12.4 l. 1439 ("≥ 300 recetas validadas antes de la fase 3"); HU-06 l. 1268; Annex B 12.
- **Descripción:** BEDCA tiene condiciones "a verificar" (l. 384); USDA está en inglés, así que la búsqueda por trigramas de "platno" no encontrará "Banana, raw" sin traducir los nombres; Open Food Facts es ODbL con *share-alike* (l. 386). El planificador necesita ≥ 300 recetas validadas con ingredientes emparejados, pero no se dice de dónde salen ni quién las crea. Escribir 300 recetas con ingredientes en gramos son decenas de horas. La guía acabó generando datos aproximados no oficiales (Annex B 12).
- **Por qué importa:** sin catálogo no hay registro manual, ni emparejamiento de fotos, ni menús. Es una dependencia de datos en la ruta crítica que no aparece en la planificación.
- **Propuesta:** añadir en §4.8 una subsección "Catálogo semilla del MVP": *"≈ 300–500 alimentos genéricos en español (importados de BEDCA si la licencia lo permite, a verificar; si no, USDA con nombres traducidos y revisados) y 40–60 recetas propias, suficientes para demostrar el planificador. Los valores de desarrollo se marcan `referenciaExterna = DEV`."* Rebajar el ≥ 300 de §12.4 a "objetivo de producto". Añadir una tarea "Preparar catálogo semilla" con horas en §11.2.

#### P-07 · El emparejamiento foto→catálogo ignora el estado crudo/cocinado y la preparación — **Alto**

- **Dónde:** §4.3 l. 319 y pasos 3-4 (l. 327-328); §4.1 principio 2.
- **Descripción:** el LLM devuelve `{nombreDetectado, gramosEstimados}`, que se empareja por trigramas con el catálogo. Pero el mismo nombre ("arroz", "pasta", "lentejas") tiene una densidad energética 2–3 veces distinta en crudo y en cocido, y el aceite de fritura o de aliño (la mayor fuente de error calórico en un plato) es invisible. Los gramos estimados en una foto son siempre de producto cocinado, mientras que el catálogo genérico suele estar en crudo y `RecetaIngrediente.cantidadGramos` también.
- **Por qué importa:** este error es sistemático, no aleatorio. Puede superar con creces el ±20–40 % de la estimación de porciones (l. 290) y contradice el objetivo OBJ-04 de fiabilidad.
- **Propuesta:** ampliar la salida del modelo a `{nombreDetectado, preparacion (CRUDO|COCIDO|FRITO|PLANCHA|…), gramosEstimados, confianza, alternativas[]}` y hacer que el emparejamiento priorice alimentos del catálogo con ese estado (el catálogo semilla debe incluir variantes cocinadas de los básicos). Añadir un candidato fijo "Aceite de oliva (estimado)" cuando la preparación sea frito o salteado, siempre editable. Documentar la limitación en §12.4.

#### P-08 · Faltan endpoints para funcionalidades y pantallas descritas — **Alto**

- **Dónde:** §7 frente a §3 y `DESIGN.md` §7.
- **Descripción (cada hueco con su origen):**

| Necesidad | Origen | Endpoint ausente |
|---|---|---|
| Cola "Pendientes de validar" del admin | DESIGN §7.17, RF-35 | No hay `GET /admin/alimentos?validado=false`. `GET /alimentos` solo devuelve "públicos + privados propios" (l. 992), así que el admin no puede ver los privados de otros usuarios que debe validar |
| Añadir una receta a un menú / eliminar una comida planificada | §3.D l. 212, DESIGN §7.10 y §7.11 ("Añadir al plan", "eliminar") | `POST /menus/{id}/comidas`, `DELETE /menus/{id}/comidas/{comidaId}` |
| Reenviar el email de verificación | DESIGN §7.2 ("Reenviar") | `POST /auth/reenviar-verificacion` |
| Sesiones activas | DESIGN §7.16 | `GET /cuenta/sesiones` (o eliminarlo del diseño) |
| Revocar el consentimiento de salud | §8.3 l. 1126, DESIGN §7.16 | Ver P-02 |
| Alimentos frecuentes | §3.C l. 183, DESIGN §7.6 | Solo existe `/alimentos/recientes` |
| Receta favorita | DESIGN §7.11 | Sin entidad ni endpoint (hoy solo hay menús favoritos) |
| Ingredientes de una receta a la lista de la compra | DESIGN §7.11 | Sin endpoint |
| Valorar una respuesta del asistente (👍/👎) | DESIGN §7.15 | Sin endpoint ni atributo (la guía lo añadió, Annex B 11) |
| Publicar/despublicar receta | §2.2 l. 121 | La guía añadió `PATCH /admin/recetas/{id}/publicacion` |
| Ver el recurso de una incidencia (foto) | §2.2 l. 115 | La guía añadió `GET /admin/incidencias/{id}/imagen` |
| Retención de fotos configurable | §5.8 l. 533 | Sin atributo ni endpoint |

- **Por qué importa:** el frontend descubrirá estos huecos a mitad de desarrollo, lo que obligará a rehacer trabajo en las dos capas.
- **Propuesta:** añadir los endpoints que entren en el MVP recortado (P-01) y **eliminar del diseño** las acciones que queden fuera (sesiones activas, receta favorita, valoración), en lugar de dejar promesas sin respaldo. Añadir al final de §7 una matriz breve "pantalla → endpoints".

#### P-09 · Flujo de subida de imágenes ambiguo (multipart vs. URL prefirmada) y formatos poco viables — **Alto**

- **Dónde:** §4.3 l. 318 y paso 1 (l. 325); §5.1 l. 438-439 (`SPA -.->|PUT/GET prefirmado| OBJ`); §5.8 l. 532; §7.4 l. 1016 ("Subir foto (multipart) **o** confirmar subida prefirmada"); Annex B 5.
- **Descripción:** se proponen dos caminos de subida. Con el PUT prefirmado, el navegador sube directamente al *bucket* sin pasar por la validación de firma de bytes ni por la eliminación de EXIF que §4.3 y §8.4 exigen "**antes** de almacenar". El backend tendría que descargar, validar, recodificar y volver a subir, y MinIO tendría que estar expuesto al navegador con CORS. Además, se aceptan HEIC y se convierte a WebP (l. 318, 532): decodificar HEIC y codificar WebP en Java requiere librerías nativas o adicionales (a verificar). La guía lo redujo a JPEG/PNG.
- **Por qué importa:** es el flujo de la funcionalidad estrella del MVP, y la ambigüedad obliga a duplicar código o a romper una garantía de privacidad.
- **Propuesta:** en el MVP, **solo multipart al backend** (validar → recodificar a JPEG, lo que elimina EXIF → miniatura → guardar). Las URL prefirmadas, solo para **lectura** (GET de 5 min). Formatos de entrada: JPEG/PNG/WebP. El cliente convierte HEIC a JPEG (como ya prevé §5.2 l. 453). Corregir §5.1, §5.8 y §7.4 en consecuencia.

#### P-10 · Contrato de API incompleto: sin DTO ni catálogo de códigos de error — **Medio**

- **Dónde:** §5.3 l. 461 (menciona DTO con MapStruct); §5.4 l. 490-491; §7 completo.
- **Descripción:** §7 solo lista métodos, rutas y permisos. No hay cuerpos de petición y respuesta para ningún flujo (por ejemplo, ¿qué devuelve `GET /analisis/{id}`?, ¿qué acepta `POST /analisis/{id}/confirmar`?) ni códigos de error de dominio (la guía introdujo `EMAIL_NO_VERIFICADO`, por ejemplo). Tampoco se indica qué devuelve `POST /menus/generar`: ¿`201` + `Location`, o `202` si tarda?
- **Por qué importa:** el contrato entre frontend y backend es lo que permite desarrollar las dos capas en paralelo. OpenAPI autogenerado (RNF-15) documenta lo implementado, pero no sirve como diseño previo.
- **Propuesta:** añadir §7.11 "DTO principales" con **solo 5–6 flujos clave** (registro/login, `PerfilRequest`/`ObjetivosResponse`, `RegistroComidaRequest` con `AlimentoRegistradoRequest`, `AnalisisResponse` con candidatos, `ConfirmarAnalisisRequest`, `ResumenDiarioResponse`) y §7.12 "Códigos de error" en una tabla `código · HTTP · cuándo` (unos 12 códigos). No hace falta más.

#### P-11 · Clasificación de IA inflada y el principio 4.1.2 contradice el OCR por LLM — **Medio**

- **Dónde:** §4.2 l. 301 ("Generar menús… Híbrido") y l. 304 ("Detectar patrones… Sí"); §4.5 l. 356-358; §6.1 `MenuSemanal.generadoPorIa` (l. 665); DESIGN §7.10 ("Generar con IA"); §4.1 l. 289 frente a §4.7 l. 376-377.
- **Descripción:**
  - El motor de menús de §4.5 es un "servicio Java puro, determinista" con filtro, puntuación y búsqueda voraz con *backtracking*: un algoritmo heurístico, no IA ni un sistema de recomendación aprendido. Etiquetarlo como "Híbrido" o "Generar con IA" es inexacto.
  - Los "patrones" del MVP+ son reglas y estadística (§4.6, Annex B 15), no ML.
  - §4.1.2 dice que "ningún valor nutricional mostrado procede directamente de un LLM", pero en el OCR (§4.7) los valores **sí** los lee un LLM multimodal de la etiqueta. Están validados por reglas y por el usuario, pero proceden del modelo.
- **Por qué importa:** en la defensa, un tribunal técnico detectará la exageración. La honestidad en la tabla "qué es IA" es precisamente uno de los puntos fuertes del documento, y conviene mantenerla.
- **Propuesta:** en §4.2, "Generar menús → Algoritmo heurístico con reglas (no IA); el LLM solo redacta la explicación (opcional)"; "Detectar patrones → Estadística y reglas (MVP+); ML solo en visión de producto". Renombrar `generadoPorIa` a `generadoAutomaticamente` y el botón a "Generar menú". Reescribir §4.1.2: *"Los valores nutricionales proceden del catálogo. La única excepción es el OCR de etiquetas, donde el modelo transcribe los valores impresos, que se validan por reglas y los confirma el usuario antes de guardarse."*

#### P-12 · Funciones LLM de poco valor que añaden complejidad al MVP — **Medio**

- **Dónde:** §4.2 l. 302; §4.4 l. 345-346 (recetas nuevas propuestas por el LLM con emparejamiento y "nutrientes incompletos"); §3.H l. 262 ("Cámbiame la cena del jueves", acción de escritura); §12.2 l. 1413 (explicaciones de menús).
- **Descripción:** las variantes de recetas generadas por el LLM obligan a emparejar ingredientes, gestionar el estado "nutrientes incompletos" y validarlas. Las acciones de escritura desde el chat obligan a diseñar un flujo de confirmación en la UI. Las explicaciones de menús aportan poco frente a la tabla de totales.
- **Propuesta:** mover a "Visión de producto" las variantes de recetas por LLM y las acciones de escritura del asistente. En el MVP/ampliación, el asistente es **de solo lectura** con 3 herramientas (`getResumenDiario`, `getComidas`, `getEntrenamientos`). Eliminar las dos viñetas de l. 345-346 o reducirlas a una frase.

#### P-13 · Numeración de fases incoherente entre §4, §5 y §11; estado de los *insights* contradictorio — **Medio**

- **Dónde:** §4.2 l. 304 ("fase 2–3"); §4.6 l. 366-367 ("Fase 1 (MVP+)", "Fase 3 (futuro)"); §5.2 l. 455 ("PWA (fase 2)"); §11.1 l. 1355-1356 (PWA "Importante", *insights* con ML "Futuro"); §11.2 (Fase 3 = Planificación, Fase 5 = PWA); RF-27 l. 1194 ("Futuro"); §7.7 l. 1059 (`/seguimiento/insights` sin marca); §3.H l. 263; DESIGN §7.4 (tarjeta "Patrón detectado" en el dashboard del MVP).
- **Descripción:** "fase" significa cosas distintas en cada sección. Los *insights* son a la vez futuros (RF-27), MVP+ (§4.6) y parte del dashboard del MVP (DESIGN §7.4).
- **Propuesta:** usar siempre las etiquetas de prioridad (MVP / Ampliación / Visión) en lugar de "fase N" fuera de §11; marcar en §7 los endpoints que no son MVP; decidir que los *insights* por reglas son "Ampliación" y que el dashboard del MVP muestra la tarjeta solo si existen.

#### P-14 · El gasto por MET se cuenta dos veces con el gasto basal — **Medio**

- **Dónde:** §3.B l. 169 ("basal × factor de actividad + gasto del entrenamiento (MET)"); §3.G l. 249 (`kcal = MET × peso × horas`); Annex B 3.
- **Descripción:** `MET × peso × horas` es el gasto **bruto**, que incluye el metabolismo en reposo (1 MET) de ese tiempo, ya contado en el basal × factor. Además, si el factor de actividad estándar (1,2–1,9) ya incluye ejercicio, se cuenta dos veces. La guía tuvo que concretar que el factor solo refleja la actividad fuera del entrenamiento.
- **Propuesta:** en §3.B y §3.G: *"Gasto neto del entrenamiento = (MET − 1) × peso (kg) × horas. El factor de actividad (1,2–1,5) refleja solo la actividad fuera del entrenamiento."* Es un cambio de una línea con un impacto visible en los objetivos.

#### P-15 · Estados de cuenta, verificación y bloqueo temporal contradictorios o sin modelar — **Medio**

- **Dónde:** §3.A l. 149 y l. 151; HU-02 l. 1245-1246; §8.1 l. 1112 y l. 1114; §6.1 `Usuario.estado` y `emailVerificado` (l. 590-591); HU-01 l. 1240; Annex B 8 y 9.
- **Descripción:**
  - ¿Puede iniciar sesión un usuario sin verificar? HU-02 habla de "cuenta verificada" y §3.A sugiere que primero se verifica, pero §8.1 solo exige la verificación "antes de usar funciones de IA" (lo que implica que sí puede entrar). La guía eligió permitir el login.
  - `estado = PENDIENTE_VERIFICACION` y `emailVerificado` son redundantes.
  - El "bloqueo temporal" tras 5 intentos no se modela (no hay `bloqueadoHasta` ni contador) y se confunde con el `BLOQUEADO` administrativo.
  - Bloquear **la cuenta** por intentos permite a un atacante bloquear a cualquier usuario cuyo email conozca (DoS).
  - HU-01 exige no revelar si el email existe, pero no dice qué responde el registro (la guía: 202 + aviso por correo).
- **Propuesta:** elegir una regla y escribirla en §3.A: *"Se permite el login sin verificar; las funciones de IA y la exportación exigen email verificado."* Eliminar `emailVerificado` o el estado `PENDIENTE_VERIFICACION`. Sustituir el bloqueo de cuenta por **limitación por IP + cuenta con retardo progresivo** (Bucket4j ya está previsto), sin cambiar el estado. En HU-01, *"El registro responde siempre 202; si el email ya existe, se envía un aviso al titular."*

#### P-16 · SSE con JWT en cabecera: incompatible con `EventSource` y con `POST` — **Medio**

- **Dónde:** §4.3 l. 329; §4.4 l. 341; §7.4 l. 1019 (`GET /analisis/{id}/eventos`); §7.9 l. 1083 (`POST …/mensajes` con respuesta SSE); §5.2 l. 450 (token en memoria y cabecera).
- **Descripción:** la API nativa `EventSource` del navegador solo hace `GET` y no permite añadir la cabecera `Authorization`. El streaming de una respuesta a `POST` requiere `fetch` con `ReadableStream` y un parser de SSE propio en Angular (`HttpClient` no expone el stream de forma cómoda). El documento no lo menciona y el SSE del análisis es redundante con el *polling* (Annex B 6 lo pospone).
- **Propuesta:** eliminar `GET /analisis/{id}/eventos` (solo *polling*). Para el asistente, indicar en §5.2: *"El streaming se consume con `fetch` + `ReadableStream` (el token va en cabecera); si se pospone, se devuelve la respuesta completa."* Un asistente sin streaming es una simplificación válida para el MVP.

#### P-17 · Tecnologías sobredimensionadas para el alcance — **Medio**

- **Dónde:** §5.2 l. 448 (`@ngrx/signals`), l. 454 (`@angular/localize`), l. 455 (PWA); §5.5 l. 497 (mención RS256); §5.3 l. 463 (logs JSON con `traceId`); §5.9 l. 548 (publicación de imágenes en CI); §8.5 l. 1149 (Trivy, OWASP Dependency-Check); §5.7 (Resilience4j + Bucket4j + `UsoIA`).
- **Descripción:** varias piezas no aportan valor proporcional a su coste en un proyecto individual. `@ngrx/signals` es innecesario con *signals* y servicios. `@angular/localize` contradice "multi-idioma: futuro" (§11.1 l. 1356), aunque externalizar textos (RNF-20) sí es razonable. El *circuit breaker* de Resilience4j en una app de un usuario se reduce a un *timeout* + mensaje de degradación.
- **Por qué importa:** cada librería añade configuración, curva de aprendizaje y una posible pregunta del tribunal sin respuesta convincente.
- **Propuesta:** mantener Spring AI, Flyway, MapStruct (opcional), Bucket4j, Testcontainers, ngx-echarts y Mailpit. Pasar a "Visión de producto" `@ngrx/signals`, `@angular/localize`, PWA, Resilience4j (sustituido por *timeout* + reintento simple de Spring), Trivy y la publicación de imágenes. Una frase en §5.11: *"Criterio: solo se incorpora una tecnología si resuelve un requisito del MVP."*

#### P-18 · RNF y objetivos de éxito no verificables en un proyecto académico — **Medio**

- **Dónde:** §1.5 l. 71-74 (OBJ-02, OBJ-03 requieren usuarios reales durante semanas); RNF-01 l. 1210 (100 usuarios concurrentes), RNF-03 l. 1212 (100.000 alimentos), RNF-11 l. 1220 (99,5 % sin monitorización definida), RNF-13 l. 1222 (WCAG 2.2 AA completo), RNF-18 l. 1227; §5.7 l. 522 y DESIGN §7.16 ("plan gratuito" / "Plan de IA", sin modelo de planes en ningún sitio).
- **Propuesta:** reescribir los OBJ como criterios demostrables ("registrar una comida por foto en < 30 s en la demo"; "100 % de los menús de prueba respetan las restricciones estrictas"). Ajustar los RNF a lo que se puede medir: "p95 < 300 ms con 20 usuarios simulados (JMeter/k6)", "búsqueda < 200 ms sobre el catálogo semilla", "WCAG 2.2 AA en las 5 pantallas principales, verificado con axe". Eliminar RNF-11 o marcarlo como de producto. Sustituir "plan gratuito" por "límite diario configurable" y quitar "Plan de IA" del diseño.

#### P-19 · Recetas sin franja de comida y lista de la compra que solo suma gramos — **Medio**

- **Dónde:** §6.1 `Receta` (l. 640-652) y `RecetaIngrediente` (l. 654); §4.5 l. 356 ("ajuste a macros de la franja"); §3.E l. 225; HU-14; DESIGN §7.12 (desglose "Lunes 200 g · Miércoles 150 g").
- **Descripción:** nada indica qué recetas son aptas para desayuno, comida o cena (solo hay `etiquetas` libres), así que el motor puede proponer lentejas en el desayuno. `RecetaIngrediente` solo guarda `cantidadGramos`, de modo que "6 huevos" aparecerá como "360 g de huevo" y la "normalización de unidades" de §3.E no tiene datos de partida. El desglose por día del diseño no se puede reconstruir desde `ProductoCompra`.
- **Propuesta:** añadir `Receta.franjasAptas` (array del enum `tipoComida`). Mostrar en la lista la unidad casera cuando el alimento tenga `porcionesJson` con "unidad" (división entera). Quitar el desglose por día del diseño o añadir `ProductoCompra.desgloseJson`.

#### P-20 · La mayoría de edad solo se comprueba en el perfil, después de consentir — **Medio**

- **Dónde:** §1.3 l. 57; §8.6 l. 1158; §6.1 `PerfilDeportivo.fechaNacimiento` "Edad ≥ 18" (l. 599); HU-01 (sin edad); DESIGN §7.2.
- **Descripción:** el registro recoge el consentimiento explícito de datos de salud sin comprobar la edad. La fecha de nacimiento llega después, en el asistente de perfil, así que un menor puede crear la cuenta y consentir.
- **Propuesta:** añadir al registro (RF-01, HU-01, DESIGN §7.2) un checkbox "Declaro ser mayor de 18 años" o la fecha de nacimiento, con un criterio de aceptación: *"Dado que no declaro ser mayor de edad, no puedo completar el registro."*

#### P-21 · Costes: proveedor sin elegir y sin coste de desarrollo ni modo simulado — **Medio**

- **Dónde:** §12.2 l. 1404-1418.
- **Descripción:** las cifras por petición son plausibles como orden de magnitud y la suma es correcta (0,34–1,66 €), pero son **precios a verificar** con el proveedor que se elija, y ese proveedor no se elige. No se estima lo que importa al estudiante: el coste de **desarrollar y probar** (cientos de llamadas en tests y depuración) ni si hay capa gratuita. La infraestructura ("decenas de euros/mes", VPS 4 vCPU/8 GB) no es necesaria para una demo, que puede ejecutarse en local con Docker.
- **Propuesta:** añadir a §12.2 una fila "Desarrollo y demo: estimación de N llamadas → X € (a verificar)", la decisión de usar un **adaptador simulado** (`MockFoodRecognitionAdapter` con respuestas grabadas) para desarrollo y tests (la guía ya lo hace, paso 20) y un tope de gasto en la cuenta del proveedor. Sustituir el párrafo de infraestructura por "Demo: local con Docker Compose; despliegue opcional en un servicio con capa gratuita (a verificar)".

#### P-22 · Trazabilidad RF ↔ HU incompleta — **Bajo**

- **Dónde:** §9.1 y §10.
- **Descripción:** RF-04, RF-05, RF-08, RF-16, RF-20, RF-21, RF-27 y RF-37 no tienen historia de usuario. HU-18 tiene prioridad mixta "MVP/Importante". No hay HU para el registro por código de barras ni para la exportación/eliminación de cuenta (derecho RGPD).
- **Propuesta:** no hace falta escribir 8 HU más. Basta con añadir una columna "HU" en §9.1 (con "—" donde no aplique) y separar HU-18 en dos (MVP: CRUD; Ampliación: validación).

#### P-23 · `GET /alimentos/codigo-barras` con efectos laterales y licencia ODbL — **Bajo**

- **Dónde:** §3.C l. 184 ("alta en catálogo si no existe"); §7.3 l. 995; §4.8 l. 386.
- **Descripción:** un `GET` que crea recursos no es seguro ni idempotente en términos REST. Copiar productos de Open Food Facts al catálogo propio puede generar una "base derivada" sujeta a *share-alike* (interpretación a verificar).
- **Propuesta:** que `GET` solo consulte (catálogo, luego OFF) y devuelva el producto sin guardarlo. El alta se hace al confirmar el registro (`POST /alimentos` con `fuente = OPEN_FOOD_FACTS`). Añadir la atribución de OFF en la interfaz.

#### P-24 · Enumerados y rangos incoherentes — **Bajo**

- **Dónde:** §3.C l. 182 (incluye "otros") frente a §6.1 l. 671 (sin `OTROS`); `diasEntrenamientoSemana` 0–14 (l. 608) frente a DESIGN §7.3 (`ns-segmented` 1–7); `comidasPorDia` 3–6 (l. 610) frente a 7 franjas; `intensidad` BAJA/MEDIA/ALTA **y** RPE (l. 720-721; ¿cuál ajusta el MET?).
- **Propuesta:** un único enum `TipoComida` compartido; rango 0–7 días (las dobles sesiones se registran como entrenamientos); definir que el ajuste del MET usa `intensidad` y que el RPE es informativo.

#### P-25 · `unaccent` en índice y UUID v7: detalles técnicos no viables tal cual — **Bajo**

- **Dónde:** §5.6 l. 505-508; Annex B 1 y 2.
- **Descripción:** `unaccent()` no está declarada como `IMMUTABLE` en PostgreSQL, así que no se puede usar directamente en un índice de expresión sin una función envoltorio. UUID v7 nativo no está en PostgreSQL 16 ni en todas las versiones de Hibernate (a verificar según versión).
- **Propuesta:** escribir lo que decidió la guía: columna `nombre_normalizado` calculada en Java con índice GIN trigram, y "UUID ordenables por tiempo generados por Hibernate".

#### P-26 · `logout` exige access token; el "Mantener sesión" del diseño no tiene reflejo — **Bajo**

- **Dónde:** §7.1 l. 966 (permiso D/A); §5.5 l. 499; DESIGN §7.1 (checkbox "Mantener sesión").
- **Descripción:** si el access token ha caducado, el logout debería funcionar solo con la cookie de refresh (que tiene `Path=/api/v1/auth`). El checkbox "Mantener sesión" no tiene ninguna regla asociada (el refresh siempre dura 7 días).
- **Propuesta:** `POST /auth/logout`: permiso "P (cookie)". Eliminar el checkbox del diseño o definir que, si no se marca, la cookie es de sesión (sin `Max-Age`).

#### P-27 · Análisis huérfanos si el backend se reinicia durante un `@Async` — **Bajo**

- **Dónde:** §4.3 paso 2 (l. 326); §6.1 `AnalisisImagen.estado`.
- **Propuesta:** añadir una frase: *"Una tarea programada marca como `FALLIDO` los análisis en `PENDIENTE`/`PROCESANDO` con más de 2 minutos de antigüedad."*

---

## 4. Aspectos correctamente resueltos

- **Principio "la IA propone, el usuario decide"** (§4.1) aplicado de forma consistente: ningún resultado se guarda sin confirmación (RF-13, HU-07 "Nada se guarda hasta que pulso Confirmar").
- **Nutrientes calculados desde el catálogo**, no desde el LLM (§4.1.2, RF-14, §12.5), con **instantánea nutricional** en `AlimentoRegistrado` para que el historial no cambie retroactivamente (§6.3). Es la mejor decisión del documento.
- **Tabla "Qué usa IA y qué no"** (§4.2): distingue con claridad visión, LLM, reglas y aritmética. Solo necesita los ajustes de P-11.
- **Validación por reglas del OCR** (kcal ≈ 4P + 4HC + 9G, saturadas ≤ grasas, azúcares ≤ hidratos, §4.7): concreta y fácil de probar.
- **Puertos/adaptadores para IA** (§4.1.4, §5.7) con *prompts* versionados y `proveedor/modelo/versionPrompt` guardados en `AnalisisImagen` para reproducibilidad.
- **Herramientas del asistente con `usuarioId` desde el contexto de seguridad**, nunca desde el texto del modelo (§4.4, §8.2): mitigación correcta de *prompt injection* y de fugas entre usuarios.
- **Monolito modular** con *package-by-feature* (§5.1, §5.3) y descarte razonado de Redis, Kafka y Kubernetes (§5.11) y de Python en el MVP (§5.10).
- **Diseño JWT**: access token en memoria, refresh opaco hasheado y rotatorio con detección de reutilización por familia, cookie `HttpOnly; Secure; SameSite=Strict` con `Path` restringido, y CSRF razonado (§5.5, §8.5).
- **Control de propiedad con `404`** en lugar de `403` (§8.2) y RNF-07 con tests de autorización automatizados.
- **Fotos**: *bucket* privado, URL prefirmadas de lectura de corta duración, claves sin datos personales, eliminación de EXIF y recodificación (§5.8, §8.4).
- **Salvaguardas nutricionales** (§8.6): déficit máximo, energía mínima, derivación ante TCA, embarazo o patologías, aviso permanente y exclusión de menores.
- **Ecuaciones conocidas y sobrescribibles** (Mifflin-St Jeor, MET), con `objetivosManuales` para quien sigue a un nutricionista (§3.B).
- **Convenciones REST** coherentes: prefijo versionado, plural, Problem Details RFC 9457, `202 Accepted` para operaciones asíncronas y paginación homogénea (§5.4).
- **Trazabilidad de la fuente** de cada alimento (`fuente`, `referenciaExterna`, OBJ-04, RNF-17), visible en la interfaz.
- **Riesgos y limitaciones honestos** (§12.3, §12.4), en especial el reconocimiento de la imprecisión de las porciones a partir de una foto 2D.
- **Coherencia general con `DESIGN.md`**: las pantallas de §7 del diseño cubren todos los módulos y los componentes de §9.5 (`ns-confidence-badge`, `ns-disclaimer`) reflejan los principios de §4.1 y §8.6.

---

## 5. Mejoras prioritarias (top 10)

| # | Mejora | Problemas | Esfuerzo en el documento | Efecto en la complejidad del proyecto |
|---|---|---|---|---|
| 1 | Añadir "Contexto y alcance académico", redefinir §11.1 en MVP de entrega / Ampliación / Visión y planificar §11.2 en horas | P-01, P-13 | Medio | **Reduce** mucho |
| 2 | Corregir la afirmación de minimización, definir el ciclo de vida del consentimiento y sustituir los pilotos reales por datos ficticios | P-02, P-20 | Bajo | Neutra (añade 1 endpoint, quita pilotos) |
| 3 | Completar y unificar el ER (entidades que faltan, `PreferenciaAlimentaria`, FK análisis→alimento, borrado lógico) | P-03 | Medio | Neutra (evita retrabajo) |
| 4 | Rediseñar el modelo de alérgenos y dietas y declarar la revisión manual de alérgenos | P-05 | Bajo | Aumenta ligeramente (protege al usuario) |
| 5 | Definir el catálogo semilla (fuente, idioma, tamaño, variantes cocinadas) y rebajar las 300 recetas a 40–60 | P-06, P-07 | Bajo | **Reduce** |
| 6 | Añadir `Entrenamiento.estado` y decidir sobre `ObjetivoDiario` (o documentar la limitación) | P-04 | Bajo | Aumenta ligeramente |
| 7 | Subida solo multipart, formatos JPEG/PNG/WebP y SSE solo (opcional) en el asistente | P-09, P-16 | Bajo | **Reduce** |
| 8 | Añadir los DTO de 5–6 flujos clave, el catálogo de errores y los endpoints que faltan (o recortar las acciones del diseño) | P-08, P-10 | Medio | Neutra |
| 9 | Reclasificar honestamente la IA y recortar funciones LLM de poco valor (variantes, escritura desde el chat) | P-11, P-12 | Bajo | **Reduce** |
| 10 | Corregir la fórmula MET neta, unificar las reglas de verificación y bloqueo y hacer verificables los RNF/OBJ | P-14, P-15, P-18 | Bajo | **Reduce** |

**Contenido que sobra o se puede condensar** (para que el documento sea más claro, no más largo): las viñetas l. 345-346 (recetas nuevas por LLM); la fila "Fase 3" de §4.6; §5.10 se puede reducir a dos frases; la mención RS256 (l. 497); los indicadores OBJ-02/OBJ-03 en su forma actual; el párrafo de infraestructura VPS de §12.2; y las referencias a "plan gratuito". Los requisitos legales de producción de §8.3 (EIPD, DPA, registro de actividades) pueden quedarse, pero agrupados bajo "Requisitos para una puesta en producción", separados de lo que se implementa en el proyecto.
