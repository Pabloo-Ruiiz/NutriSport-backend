# NutriSport AI — Informe final de la auditoría

| Campo | Valor |
|---|---|
| Fecha | 22/11/2026 |
| Documentos auditados | `docs/PROJECT_PROPOSAL.md` (v1.0 → v1.1), `docs/DESIGN.md` (v1.0 → v1.1), mockups `docs/design/mockups/` (6 → 8) |
| Método | Dos agentes independientes en paralelo (*Project Reviewer* y *Design Reviewer*), solo lectura → corrección → segunda revisión por los mismos dos agentes, también en paralelo → ajustes finales |
| Informes de la primera revisión | [PROJECT_REVIEW.md](PROJECT_REVIEW.md) · [DESIGN_REVIEW.md](DESIGN_REVIEW.md) |

---

## 1. Estado inicial

La documentación era **sólida como especificación de un producto comercial**. Lo mejor resuelto era: nutrientes calculados desde el catálogo y no desde el LLM, la IA siempre corregible por el usuario, un diseño JWT bien planteado, *tokens* de diseño completos y mockups de aspecto profesional con datos coherentes.

Pero **no estaba escrita para lo que es**: un proyecto individual de 2.º DAW.

| Informe | Puntuación inicial | Problemas |
|---|---|---|
| Proyecto | **6,5 / 10** (como especificación, 8; como plan ejecutable por un estudiante, 4) | 2 críticos · 7 altos · 12 medios · 6 bajos |
| Diseño | **7,5 / 10** | 0 críticos · 7 altos · 9 medios · 6 bajos |

## 2. Principales problemas encontrados

1. **Alcance incompatible con un proyecto individual** (crítico): no se mencionaba el contexto académico, la planificación era de 21 semanas sin horas y el MVP ya superaba el tiempo disponible.
2. **Privacidad contradictoria** (crítico): se afirmaba que a la IA solo se enviaba la imagen y el perfil, cuando el asistente envía datos de salud. El consentimiento no tenía ciclo de vida y se preveían pilotos con personas reales.
3. **Modelo de datos incompleto**: el diagrama ER no coincidía con §6.1, faltaban entidades (tipos de actividad, *insights*, roles, histórico de objetivos) y no había entrenamientos planificados.
4. **Alergias**: el "filtro duro" no tenía respaldo en los datos (sin trazas, sin revisión de alérgenos y con dietas imposibles de filtrar).
5. **Fiabilidad de la IA**: el reconocimiento por foto ignoraba la preparación (crudo, cocido, aceite) y había dos caminos de subida de fotos, uno de ellos sin eliminar EXIF.
6. **API**: no había DTO ni catálogo de errores, y faltaban unos 12 endpoints que pedían las pantallas.
7. **Diseño**:
   - el color del entrenamiento era el mismo violeta que el de la IA;
   - había textos de 8–10 px;
   - el mapa de adherencia tenía poco contraste;
   - el móvil no estaba dibujado;
   - el flujo de foto solo mostraba el caso feliz;
   - "sin lactosa" aparecía junto a "yogur griego";
   - el registro manual no tenía mockup.

**Contradicciones entre los dos informes, ya resueltas:**

- El diseño pedía más pantallas y el proyecto, menos alcance. Se resolvió etiquetando cada pantalla por alcance y dibujando solo pantallas del MVP (móvil y registro manual).
- Corregir "yogur sin lactosa" (diseño) chocaba con eliminar la dieta "sin lactosa" (proyecto). Se resolvió modelando la intolerancia a la lactosa con su propio atributo (`contieneLactosa`), separado del alérgeno `LACTEOS`.
- La segunda revisión de ambos agentes coincidió en que el dashboard del MVP dependía del planificador, que no es MVP. Se resolvió con una barra de navegación propia del MVP y marcando esos bloques como Ampliación.

## 3. Cambios realizados

### PROJECT_PROPOSAL.md (v1.1)

- **Alcance:**
  - nuevo §1.7 con el contexto académico y las etiquetas **MVP / Ampliación / Visión**, aplicadas en módulos, requisitos (con columna HU), historias, endpoints y entidades;
  - §11 rehecho en horas: **MVP ≈ 315 h + 20 % de colchón ≈ 380 h**, con una lectura honesta de que la Ampliación (≈ 180 h) solo cabe si sobra tiempo;
  - en la segunda revisión, las **recetas y su catálogo pasan a la Ampliación**, junto con el planificador.
- **Privacidad (§8.3):**
  - tabla real de qué datos se envían a la IA en cada función;
  - consentimiento versionado, revocable y que se puede volver a dar;
  - declaración de mayoría de edad en el registro;
  - "ámbito académico" solo con datos ficticios;
  - requisitos de producción (DPA, EIPD) separados de lo que se implementa.
- **Datos (§6):**
  - ER completo y unificado: `UsuarioRol`, `TipoActividad`, `ObjetivoDiario`, `Insight`, FK análisis→alimento, propietarios, borrado lógico;
  - entrenamientos `PLANIFICADO / REALIZADO / OMITIDO`, con la regla de recálculo del objetivo de cada día;
  - alérgenos "contiene" y "trazas" con revisión obligatoria;
  - franjas aptas de cada receta.
- **IA:**
  - preparación detectada y candidato de aceite;
  - clasificación honesta: los menús son un algoritmo de reglas, no IA, y los *insights* son reglas;
  - asistente de solo lectura;
  - adaptador simulado para desarrollar sin coste;
  - fórmula del gasto **neto** `(MET − 1) × peso × horas`.
- **API (§7):** columna de alcance, endpoints que faltaban, **DTO principales (§7.11)**, **catálogo de códigos de error (§7.12)** y **matriz pantalla→endpoints (§7.13)**.
- **Arquitectura y seguridad:**
  - subida de fotos solo por multipart al backend, en JPEG o PNG;
  - límite de intentos de login sin bloquear cuentas;
  - un bloqueo administrativo tiene efecto inmediato;
  - tecnologías recortadas (Resilience4j, ngrx, i18n y PWA pasan a Visión).
- **Requisitos y objetivos verificables** (RNF con método de verificación) y costes de desarrollo realistas (casi cero con el modo simulado).

### DESIGN.md (v1.1)

- **Actividad en cian `#0E7490`** (5,36:1). El índigo y el violeta quedan **exclusivos de la IA**.
- **Mínimo real de 12 px.** Nuevos tokens `card-title` y textos de macros (`-text`). Badges `activity` y `restriction` (escudo).
- **Mapa de adherencia** con escala documentada, texto oscuro (≥ 4,9:1) y patrones para los días bajos y en exceso.
- **Flujo de foto con tres estados** (foto, análisis, error), detalles en solo lectura durante el análisis, *toast* de confirmación y badge "Estimación IA".
- **Tabla de alcance por pantalla.** Barra inferior **MVP** (Inicio · Comidas · Registrar · Actividad · Perfil). Nuevo §7.18 con los flujos complementarios: recuperar contraseña, verificar email, pesaje, catálogo de recetas, escáner y etiqueta.
- **Valores sincronizados con el CSS de los mockups** (botones, iconos, cantidad, sidebar y buscador), estado activo del segmentado con borde y rasgos propios de la marca.

### Mockups

- **01–06 corregidos:**
  - color de actividad;
  - restricciones coherentes ("sin lactosa");
  - confianza baja y preparación en la revisión;
  - planificador con hidratos por día, días de entreno resaltados, sin miniaturas y sin tratamiento IA;
  - estadísticas con la comparación con el periodo anterior, vista diaria/semanal y mapa de calor accesible;
  - etiquetas "Hidratos/HC" unificadas;
  - textos ≥ 12 px.
- **Nuevos:**
  - **07 Móvil (390 px):** inicio con barra inferior, foto (paso 1), error del análisis y planificador por día.
  - **08 Registro manual:** búsqueda tolerante, unidades caseras y aviso de alérgenos sin revisar.
- **Capturas PNG** regeneradas y revisadas visualmente.

## 4. Problemas corregidos y pendientes

**Proyecto:** 27 de 27 problemas iniciales corregidos. Los dos parciales de la segunda revisión (P-11 y P-24) y los 7 problemas nuevos que detectó (N-01 a N-07) se han corregido en la ronda final.

**Diseño:** de los 22 problemas iniciales, 13 estaban corregidos en la segunda revisión. En la ronda final se han corregido además M-01, M-02, M-03, M-05, M-06, M-07, M-08, M-09 y M-10, y el resto de D-02. Quedan estos pendientes, ninguno bloqueante:

| Pendiente | Gravedad | Motivo / cómo se resuelve |
|---|---|---|
| D-05 · *Dropzone* de escritorio y *toast* de confirmación sin dibujar | Bajo | Especificados en DESIGN.md §5.15 y §7.7; el paso 1 está dibujado en el mockup móvil (07) |
| D-07 · Login, configuración inicial y dashboard vacío solo en texto | Medio | Especificados en §7.1–7.4; usan componentes ya dibujados. Conviene un mockup de *onboarding* para el portfolio |
| D-12 · El panel de alternativas tapa columnas vecinas | Bajo | Ya muestra el delta de kcal. En Angular se resolverá como panel anclado o *drawer* |
| D-13 · La "línea de pulso" no aparece en los mockups | Bajo | Documentada como rasgo de marca en §1.4 |
| D-14 · Tarjeta "Reparto por comidas" sin dibujar | Bajo | Especificada en §7.14 (Ampliación) |
| D-22 · El chat empieza con un mensaje cortado | Bajo | Es el comportamiento natural del *scroll* en un chat; se acepta |
| M-04 · Las pantallas densas se rompen a 1024 px | Medio | Se acota en §8: los mockups de escritorio se validaron a ≥ 1280 px y la adaptación se hace en Angular (§6.3) |
| Horas disponibles sin confirmar | Medio | Las ≈ 400 h son una estimación: ajustar §11 al calendario real del módulo |
| Guía del backend desactualizada | **Alto** | `docs/Guia_Github_Backend_NutriSport.md` se generó con la propuesta v1.0 y contradice la v1.1 (alcance, gasto neto, alérgenos, estados de cuenta, `ObjetivoDiario`, recetas en Ampliación). No estaba entre los ficheros que esta auditoría podía modificar: **hay que regenerarla antes de seguirla** |

## 5. Puntuaciones

Las puntuaciones finales son las de la segunda revisión independiente, **antes** de la última ronda de ajustes. Son conservadoras, porque esa ronda corrigió casi todo lo que señalaron.

### Proyecto

| Apartado | Inicial | Final |
|---|---|---|
| A. Definición del proyecto | 7,5 | 8,5 |
| B. Funcionalidades | 7 | 8 |
| C. Inteligencia artificial | 7 | 8,5 |
| D. Arquitectura | 7,5 | 8,5 |
| E. Base de datos | 5,5 | 8 |
| F. API REST | 6 | 8 |
| G. Seguridad y privacidad | 7 | 8,5 |
| H. Viabilidad | 4 | 7,5 |
| **Global** | **6,5** | **8,2** |

### Diseño

| Apartado | Inicial | Final |
|---|---|---|
| A. Identidad visual | 7,5 | 8 |
| B. Sistema de diseño | 8 | 8,5 |
| C. Experiencia de usuario | 7,5 | 8,5 |
| D. Mockups | 7,5 | 8,5 |
| E. Accesibilidad y responsive | 6 | 7,5 |
| F. Profesionalidad | 8 | 8,5 |
| **Global** | **7,5** | **8,3** |

| Mockup | 01 | 02 | 03 | 04 | 05 | 06 | 07 | 08 |
|---|---|---|---|---|---|---|---|---|
| Inicial | 8,5 | 7 | 8,5 | 6,5 | 7 | 8 | — | — |
| Final | 9 | 8 | 9 | 8 | 8 | 8 | 8,5 | 8,5 |

## 6. Valoración global

NutriSport AI tiene ahora una documentación **coherente, honesta sobre lo que hace la IA y realista para un proyecto individual**:

- El MVP está cerrado y planificado en horas.
- Las decisiones de privacidad y de seguridad son defendibles ante un tribunal.
- El modelo de datos soporta todas las funcionalidades del MVP.
- El sistema de diseño y los 8 mockups tienen calidad de portfolio.

El principal riesgo que queda es el tiempo: el MVP ocupa casi todas las horas estimadas, así que la Ampliación es opcional de verdad.

**Veredicto: el proyecto está suficientemente definido para comenzar el desarrollo del MVP.**

## 7. Recomendaciones para comenzar

1. **Regenerar la guía del backend** a partir de la propuesta v1.1 antes de crear las issues.
2. **Confirmar las horas reales** del módulo y ajustar §11 si hace falta. El orden de recorte ya está escrito: primero el cambio de email, después la administración de usuarios.
3. **Empezar por las fases 0–2** (fundamentos, autenticación y perfil): no dependen de nada pendiente.
4. **Preparar el catálogo semilla en paralelo** (200–300 alimentos en español, con alérgenos revisados) y verificar antes la licencia de BEDCA. Es la dependencia de datos de la ruta crítica.
5. **Desarrollar la foto con IA con el adaptador simulado** y elegir el proveedor real al final de la fase 5, comparando precio, región y política de retención.
6. **Hacer commit de `docs/`**, que aún no está versionado, antes de empezar.

---

## Anexo · Cambios posteriores a la auditoría (v1.2, integraciones externas)

Después de esta revisión se integraron Open Food Facts, TheMealDB y USDA FoodData Central. Esos cambios **no formaban parte de la auditoría** y las puntuaciones de arriba no los incluyen.

- Nuevo `docs/API_INTEGRATIONS.md`: endpoints comprobados en la documentación oficial, límites, licencias, caché, errores, *fallback* y proceso de cálculo nutricional de recetas.
- `PROJECT_PROPOSAL.md` v1.2: entidades `AlimentoFuente`, `MapeoIngrediente` y `RecetaFavorita`; nuevos campos de `Receta`, `RecetaIngrediente` y `Alimento`; endpoints de productos, recetas y planificador; RF-38 a RF-45, HU-22 a HU-25 y fases A1–A5.
- `DESIGN.md` v1.2: componentes de fuente, estado del cálculo, ficha nutricional, alérgenos, atribución, escáner y tarjeta de receta; pantallas §7.19–§7.23.
- Mockups 09–13: buscador de productos, escáner, catálogo de recetas, detalle de receta y recetas en el planificador.
- Guía del backend regenerada (37 pasos: MVP en los pasos 1–26 y Ampliación en los pasos 27–37).

Se recomienda una revisión específica de estos cambios antes de empezar la Ampliación.
