# NutriSport AI — Auditoría de diseño (Design Review)

| Campo | Valor |
|---|---|
| Revisor | Agente 2 — Design Reviewer (UI/UX, WCAG 2.2, sistemas de diseño) |
| Fecha | 21/11/2026 |
| Material revisado | `docs/DESIGN.md` v1.0 (completo), mockups `01`–`06` (HTML + PNG), `assets/mockups.css`, `assets/app.js`, `assets/plato-salmon.svg`, `PROJECT_PROPOSAL.md` §3 y §10 |
| Comprobaciones propias | Ratios de contraste recalculados con la fórmula WCAG (script Node); capturas headless a 390 px y 1024 px; cuadre numérico de los datos entre pantallas |

---

## 1. Resumen general

El sistema de diseño es de **nivel profesional**. Los *tokens* son completos (color, tipografía, espaciado, radios, sombras, capas y movimiento) y están bien organizados en primitivos y semánticos. Los componentes están especificados con sus estados y la guía Angular es realista (CDK, señales, BEM, Stylelint). Los seis mockups son coherentes entre sí y los datos cuadran (por ejemplo, 520 + 410 + 812 = 1.742 kcal, y 938 − 718 = 220 kcal restantes), lo cual es poco habitual. **Los ratios de contraste que declara §2 son correctos**: he recalculado más de 30 y todos coinciden.

Los problemas importantes son de **coherencia semántica y de cobertura**, no de estética:

- El violeta de "actividad" es el mismo color que el de la IA.
- Hay textos de gráficos que se ven a 8–10 px, y el mapa de adherencia tiene un contraste insuficiente.
- No hay ningún mockup móvil, a pesar de que la foto y la lista de la compra son flujos *mobile-first*.
- El flujo de foto solo muestra el *happy path*.
- La restricción "sin lactosa" convive con "yogur griego".
- El registro manual de comidas (la acción más frecuente), el *onboarding* y el login no tienen mockup.

No hay problemas críticos. Ninguno de los detectados es caro de corregir.

---

## 2. Puntuación

### Puntuación global: **7,5 / 10**

| Apartado | Nota | Justificación |
|---|---|---|
| A. Identidad visual | 7,5 | Sobria, creíble y alineada con la telemetría deportiva (sidebar "cabina", cifras protagonistas). La paleta base es casi idéntica a Untitled UI y al índigo de Tailwind, así que la personalidad recae casi solo en el logotipo y la sidebar. |
| B. Sistema de diseño | 8 | Tokens completos y bien jerarquizados, inventario de componentes y código Angular coherente. Hay divergencias entre DESIGN.md y el CSS de los mockups, además de tokens sin documentar. |
| C. Experiencia de usuario | 7,5 | El dashboard responde a "¿cómo voy hoy?" y la revisión del análisis es excelente. Fallan los estados no felices del flujo de foto, la densidad del planificador y la ambigüedad del campo de descripción durante el análisis. |
| D. Mockups | 7,5 | Alta fidelidad y consistencia. Faltan estados (vacío, error, confirmación) y pantallas MVP clave, y hay alguna incoherencia de datos. |
| E. Accesibilidad y responsive | 6 | Los contrastes base están bien. Pero hay textos efectivos de 8–10 px, el mapa de calor falla en contraste y depende del color, un gris de la sidebar baja de 4,5:1 y el responsive solo existe en texto. |
| F. Profesionalidad | 8 | Parece un SaaS comercial real, no una práctica académica. Para destacar en un portfolio le falta un rasgo visual propio y los estados límite. |

### Puntuación por mockup

| Mockup | Nota | Principal problema |
|---|---|---|
| 01 Dashboard | 8,5 | No existe la variante de estado vacío que exige HU-10, y la cabecera difiere de §7.4. |
| 02 Registro con foto | 7 | Solo muestra el estado "analizando". Faltan el *dropzone* inicial de §7.7 y los estados de error o sin alimentos detectados. |
| 03 Resultado del análisis | 8,5 | Expone etiquetas del modelo en inglés ("«salmon fillet»") y no muestra qué ocurre tras "Confirmar y guardar". |
| 04 Planificador | 6,5 | Rejilla monótona con textos de 10–11,5 px. El *popover* tapa dos columnas y aparece "Yogur griego" con la restricción "Sin lactosa". |
| 05 Estadísticas | 7 | Ejes y mapa de calor a unos 8–9 px efectivos, texto blanco sobre verde a 2,62:1, y el interruptor "Comparar" no tiene ningún efecto visible. |
| 06 Asistente IA | 8 | La misma restricción se pinta en rojo (error) aquí y en verde en el 04. Hay dos botones primarios verdes. A 1024 px el layout se rompe. |

---

## 3. Problemas detectados

### 3.1. Tabla resumen

| ID | Apartado / mockup | Título | Gravedad |
|---|---|---|---|
| D-01 | B / 01, 04, 05, 06 | El color de "actividad" es el violeta de la IA, y "Pre-entreno" usa el badge IA | Alto |
| D-02 | E / 01, 04, 05 · §3.2 | Textos efectivos < 12 px y contradicción interna en la escala | Alto |
| D-03 | E / 05 | Mapa de adherencia: contraste insuficiente e información solo por color | Alto |
| D-04 | E / todos · §8 | Responsive sin ningún mockup; el layout se rompe a 1024 px y en móvil | Alto |
| D-05 | C / 02, 03 | El flujo de foto solo cubre el *happy path* | Alto |
| D-06 | D / 01, 04, 06 | Incoherencia de la restricción "sin lactosa" entre pantallas | Alto |
| D-07 | D · §7 / §8 | Pantallas MVP sin mockup (registro manual, *onboarding*, login, estados vacíos) | Alto |
| D-08 | B / CSS mockups | Divergencias entre DESIGN.md y el CSS de los mockups, y tokens sin documentar | Medio |
| D-09 | E / sidebar, topbar | Etiquetas de grupo de la sidebar (4,29:1) y borde del buscador (1,47:1) bajo umbral | Medio |
| D-10 | C / 02 | "Describe tu plato" sigue editable cuando el análisis ya está en marcha | Medio |
| D-11 | C / 04 | Planificador poco escaneable y sin carbohidratos por día | Medio |
| D-12 | D / 04 · §7.10 | El panel de alternativas es un *popover* que tapa contenido, en contra del §7.10 | Medio |
| D-13 | A · §1.1, §2 | Paleta de base genérica (Untitled UI + Tailwind) frente a la promesa de "no de plantilla" | Medio |
| D-14 | C / 05 | "Comparar" sin efecto; faltan medias semanales (HU-15) y el reparto por comidas | Medio |
| D-15 | E · §5.4 | Estados seleccionados con contraste casi nulo (`ns-segmented`) | Medio |
| D-16 | Coherencia §7 ↔ propuesta | Funcionalidades de la propuesta sin pantalla definida | Medio |
| D-17 | D / 01, 03, 05 | Microcopy: "En línea", etiquetas del modelo en inglés, abreviaturas | Bajo |
| D-18 | D / 01, 04, 06 · §5.8, §7.4 | La cabecera y la franja pre-entreno no coinciden entre pantallas | Bajo |
| D-19 | B / 06 · §2.7 | La regla de un solo primario verde choca con el CTA global de la topbar | Bajo |
| D-20 | B / 02, 03 · §2.7, §5.15 | El badge "Estimación IA" y el umbral de "Continuar manualmente" no están unificados | Bajo |
| D-21 | D / 01, 05 | Rangos de fechas ligeramente incoherentes | Bajo |
| D-22 | D / 06 | La captura empieza con una tarjeta cortada | Bajo |

**Recuento:** 0 críticos · 7 altos · 9 medios · 6 bajos.

### 3.2. Detalle

#### D-01 · El color de "actividad" es el violeta de la IA, y "Pre-entreno" usa el badge IA (Alto)

- **Dónde:**
  - DESIGN.md §2.4: `--ns-data-activity: #7A5AF8`, que es igual a `--ns-ai-violet`, y en §9.1 `--ns-data-activity: var(--ns-ai-violet)`.
  - Mockup 01: el anillo interior "Entreno", la tarjeta "Entrenamiento de hoy" con tinte violeta y los puntos de "Día de entreno" del gráfico.
  - Mockup 04: los badges "Pre-entreno" usan la clase `badge--ai` y el icono de entreno de la cabecera del día es violeta.
  - Mockup 05: las barras de "Entreno" en "Entreno vs. descanso".
- **Descripción:** §1.2 y §2.7 establecen que el índigo/violeta identifica todo lo que hace la IA, "de modo que el usuario sabe siempre qué es estimado por la IA y qué es dato confirmado". Sin embargo, el mismo tono codifica el entrenamiento, que es un dato del usuario.
- **Por qué importa:** es la regla semántica central de la marca. En el dashboard, la tarjeta de entrenamiento y la de "Patrón detectado" (IA) quedan una encima de la otra con el mismo violeta, así que el usuario no puede saber cuál es de la IA.
- **Solución:**
  - Asignar a actividad un tono propio fuera de la familia índigo y verde, por ejemplo cian `#0E7490` (5,36:1 sobre blanco) con su tinte `#ECFEFF`, y actualizar §2.4, §9.1 y el tema ECharts.
  - En el 04, sustituir `badge--ai` por un badge `activity` o `neutral` con icono `zap`.
  - Añadir a §2.7: "La familia índigo/violeta es exclusiva de la IA".

#### D-02 · Textos efectivos < 12 px y contradicción interna en la escala (Alto)

- **Dónde:**
  - §3.2 define `overline` a 11 px y la regla del mismo apartado dice "nunca menos de 12 px".
  - En el CSS de los mockups:
    - `.chart-axis` 11 px dentro de SVG escalados por `viewBox`. El peso del 05 (viewBox 520, renderizado a ~420 px) queda en unos 8,9 px, y los ejes del mapa de calor (viewBox 320, renderizado a ~240 px) en unos 8,3 px.
    - El badge "Pre-entreno" del 04 está a 10 px en línea, el *pill* "Hoy" a 10 px y `.slot__meta` a 11,5 px.
    - La tira de fechas del 01, `.kbd`, el contador de la sidebar y la leyenda del mapa de calor están a 11 px.
- **Por qué importa:** contradice la propia norma del manual. En el 05, las etiquetas de los ejes del peso ("72,5", "25 oct", "Objetivo 70,5 kg") apenas se leen en la captura de 1440 px. Además, con ECharts el texto no escalará con el SVG, así que el mockup no representa lo que se implementará.
- **Solución:**
  - Fijar en §3.2 un mínimo de 12 px para todo texto, subiendo `overline` a 12/16, o declarar explícitamente la excepción solo para `overline` en mayúsculas con peso 600.
  - En los mockups, poner `font-size` fijo en los SVG (`vector-effect` no aplica a texto, así que hay que dimensionar el viewBox con el ancho real) o generar los gráficos con el tamaño final.
  - Subir a 12 px "Pre-entreno", "Hoy" y `.slot__meta`.

#### D-03 · Mapa de adherencia: contraste insuficiente e información solo por color (Alto)

- **Dónde:** mockup 05, tarjeta "Adherencia diaria". Los números de día van en blanco sobre `#12B76A` (2,62:1, en 7 celdas) y sobre `#0B9A5D` (3,62:1, en 7 celdas), con un tamaño efectivo de unos 8 px. El propio §2.1 dice de `green-500`: "No usar para texto sobre blanco (2,62:1)".
- **Descripción:**
  - El nivel de adherencia solo se distingue por la intensidad del verde, con 4 tonos cercanos y ningún valor numérico ni patrón.
  - La leyenda incluye "Exceso" (ámbar), pero ninguna celda lo usa.
  - §5.13 define la escala `green-50 → green-600`, mientras que el mockup usa hasta `green-700` y añade el ámbar.
- **Por qué importa:** incumple WCAG 1.4.3 (contraste de texto) y 1.4.1 (uso del color), y §10 promete "nunca transmitir información solo con color".
- **Solución:**
  - Usar texto `ink-950` en todas las celdas (7,14:1 sobre `#12B76A` y 12,34:1 sobre `#6CE9A6`), o solo blanco sobre `green-700` (5,37:1).
  - Añadir un *tooltip* o una tabla alternativa con el % de cada día.
  - Marcar con un patrón o icono los días en "Exceso" y en "Bajo".
  - Actualizar §5.13 con la escala real de 4 pasos más el ámbar y con estas reglas de texto.

#### D-04 · Responsive sin ningún mockup; el layout se rompe a 1024 px y en móvil (Alto)

- **Dónde:** §8 dice "Los mockups representan la vista de escritorio (≥ 1280 px)". He hecho capturas propias:
  - A 390 px, la sidebar sigue ocupando 248 px y el contenido queda en una columna de unos 140 px con el anillo cortado.
  - A 1024 px, en el 06 el panel de contexto no se oculta (§7.15 dice que en `lg` sí se oculta). El chat queda en unos 200 px, el compositor se deforma y la cabecera "Contexto de hoy" se solapa con el icono de editar.
- **Descripción:** toda la adaptación (bottom-nav con botón de cámara §6.4, *bottom sheets*, tablas apiladas, planificador por día) existe solo como texto.
- **Por qué importa:** los flujos con más valor son móviles. La foto se hace con el móvil (§7.7, `capture="environment"`) y la lista de la compra es "*mobile-first*" (§7.12). Además, un evaluador o reclutador redimensionará la ventana.
- **Solución:**
  - Añadir 2 o 3 mockups móviles de 390 px: el dashboard con bottom-nav, la captura y revisión de la foto con totales fijos abajo, y el planificador en vista por día.
  - Añadir al menos un *media query* a `mockups.css` que convierta la sidebar en bottom-nav por debajo de 768 px.
  - Cambiar la nota de §8 por "Mockups móviles: …".

#### D-05 · El flujo de foto solo cubre el *happy path* (Alto)

- **Dónde:**
  - Mockup 02: solo el estado "Analizando".
  - Mockup 03: solo la revisión con 4 alimentos y confianza ≥ 63 %.
  - §7.7 describe un *dropzone* inicial que no está dibujado.
  - HU-07 exige ofrecer el registro manual si el análisis falla o tarda más de 30 s.
- **Descripción:** faltan cinco estados:
  1. Foto aún no elegida (*dropzone* + "Hacer foto / Subir archivo").
  2. Error de análisis o IA no disponible.
  3. "No hemos detectado alimentos".
  4. Alimento con confianza baja (< 50 %, badge de error).
  5. Confirmación tras guardar.
- **Por qué importa:** es el flujo diferencial del producto, y los estados de fallo son los que deciden si el usuario confía en la IA. §5.15 y §11 ya tienen el patrón y el microcopy ("No hemos podido analizar la foto…"), pero ningún mockup demuestra cómo se integran.
- **Solución:**
  - Añadir al 02 una variante "Paso 1 · Foto" (el *dropzone* de §7.7) y una variante de error con `ns-alert` + "Reintentar" + "Continuar manualmente" conservando la foto.
  - En el 03, mostrar un alimento con "Confianza baja · 42 %" resaltado.
  - En §7.8, definir la confirmación: un *toast* "Cena guardada · 718 kcal · Deshacer" y la vuelta al dashboard con el anillo actualizado.

#### D-06 · Incoherencia de la restricción "sin lactosa" entre pantallas (Alto)

- **Dónde:**
  - Mockup 04: el resumen dice "Restricciones aplicadas: Sin lactosa", pero el planificador incluye "Yogur griego con nueces", "Yogur griego con granola" y "Yogur con fruta".
  - Mockup 01: "Yogur griego con nueces y miel".
  - Mockup 05: solo aquí aparece "Yogur griego s/ lactosa".
  - Mockup 06: el chip "Sin lactosa · estricta" es rojo (`badge--error`), mientras que en el 04 es verde (éxito).
- **Por qué importa:** la propuesta define las restricciones como "filtro duro" (módulo D) y HU-05 dice que ninguna propuesta las incumple. Un evaluador que vea "yogur griego" junto a "sin lactosa" concluirá que el filtro falla. Además, la misma entidad cambia de color semántico, y el rojo se reserva para error (§2.6).
- **Solución:**
  - Renombrar en el 01 y el 04 a "Yogur griego sin lactosa" (o sustituirlo por "Yogur de soja").
  - Definir en §5.10 una variante única para restricciones, `neutral` + icono `shield-check`, y usarla en el 04 y el 06.

#### D-07 · Pantallas MVP sin mockup (Alto)

- **Dónde:** §8 cubre 6 de las 17 pantallas. No hay mockup de:
  - Registro de comidas manual (§7.6, HU-06, MVP).
  - Configuración inicial (§7.3, HU-04, MVP).
  - Login y registro (§7.1–7.2).
  - La variante vacía del dashboard (HU-10: "veo un estado vacío con accesos directos").
- **Por qué importa:** el registro manual es la acción más repetida de la app y la que más riesgo de usabilidad tiene: *split* 5 + 7, *combobox*, unidades caseras y resumen fijo en móvil. Para el portfolio, el login y el *onboarding* son además la primera impresión.
- **Solución:** priorizar tres mockups:
  - 07 Registro manual: buscador con "platno" → "Plátano", comida en construcción y selector "1 unidad mediana".
  - 08 *Onboarding*: paso 3 "Tu objetivo" más el resumen final con el anillo.
  - 09 Login.
  - Más la variante vacía del 01. El resto puede quedarse en texto.

#### D-08 · Divergencias entre DESIGN.md y el CSS de los mockups, y tokens sin documentar (Medio)

- **Dónde:**

  | Elemento | DESIGN.md | CSS de los mockups (`mockups.css`) |
  |---|---|---|
  | Título de tarjeta (§5.5) | `h3` = 18/26 | `.card__title` 16/24 |
  | Botón `lg` (§5.1) | 16 px | 15 px (`.btn--lg`) |
  | Icono de botón (§4, §5.1) | 20 px | 18 px |
  | `ns-quantity-input` (§5.2) | alto 40 px | 36 px |
  | Grupos de la sidebar (§5.7) | `ink-400` | `#6B7A90`, que no existe en la paleta |
  | Texto de los badges de macros (§5.10) | "color de datos oscurecido", sin HEX | `#1D4FBF`, `#8A5A00`, `#A8285A` |

  Además, aparecen tamaños fuera de la escala: 12,5, 11,5 y 15 px.
- **Por qué importa:** DESIGN.md se declara "fuente de verdad". Si los mockups, que dicen compartir "los *tokens* de este documento" (§8), no la respetan, el desarrollador no sabrá qué valor implementar.
- **Solución:**
  - Alinear el CSS con el manual, o actualizar el manual si el valor del mockup es el preferido (16 px para títulos de tarjeta es razonable: cambiar `h3` o crear un token `card-title`).
  - Añadir a §2.4 los tres HEX de texto de macro como `--ns-data-*-text`.

#### D-09 · Etiquetas de grupo de la sidebar y borde del buscador bajo umbral (Medio)

- **Dónde:**
  - `.sidebar__group` usa `#6B7A90` sobre `ink-950`, que da **4,29:1**, en texto de 11 px ("PRINCIPAL", "RENDIMIENTO"…).
  - `.search` (topbar en todos los mockups) usa `border-strong #D0D5DD`, que da **1,47:1** sobre blanco.
- **Por qué importa:** la sidebar incumple 1.4.3 (exige 4,5:1). En el buscador, §2.5 creó precisamente `--ns-border-input` (3,63:1) para cumplir 1.4.11 con los controles, y el buscador global no lo usa.
- **Solución:** usar `ink-400` (7,30:1) en los grupos de la sidebar y `border-input` en `.search` y en el compositor del chat.

#### D-10 · "Describe tu plato" sigue editable cuando el análisis ya está en marcha (Medio)

- **Dónde:** mockup 02, panel "Detalles de la comida". El campo "Describe tu plato (opcional)" está enfocado y relleno mientras la fase 2 "Detectando alimentos" ya corre, y el stepper marca "Foto" como completado.
- **Por qué importa:** el texto se anuncia como "mejora la precisión", pero el usuario no sabe si se ha enviado con la foto. Si lo edita durante el análisis, la expectativa queda rota. Esto contradice el modelo mental del stepper "Foto → Análisis".
- **Solución:** en §7.7, especificar que la descripción, el tipo y la hora se rellenan en el paso "Foto", antes de pulsar "Analizar" (botón `ai`). Durante el análisis, mostrarlos en solo lectura con "Editar y reanalizar". Actualizar el 02 en consecuencia.

#### D-11 · Planificador poco escaneable y sin carbohidratos por día (Medio)

- **Dónde:** mockup 04, rejilla de 7 × 5.
- **Descripción:**
  - Son 35 tarjetas blancas idénticas, con miniaturas abstractas de 28 px que no aportan información.
  - Los nombres van a 12,5 px y los metadatos a 11,5 px.
  - Cada celda muestra solo kcal y P, aunque §7.10 dice "kcal y P/C/G".
  - El resumen anuncia "4 días de entreno con más carbohidratos", pero ningún dato de la rejilla permite verificarlo (HU-12).
- **Por qué importa:** el valor diferencial del planificador es adaptar la nutrición a la carga deportiva, y la pantalla no lo hace visible. La densidad dificulta escanear la semana.
- **Solución:**
  - En la cabecera de cada día, añadir una mini barra o cifra de carbohidratos (p. ej. "C 380 g") junto a las kcal, resaltada en los días de entreno.
  - Eliminar las miniaturas o hacerlas útiles (≥ 40 px con la foto real).
  - Subir los textos a 13 y 12 px.
  - Diferenciar las columnas de los días de entreno con un fondo tenue del color de actividad (ver D-01).

#### D-12 · El panel de alternativas es un *popover* que tapa contenido (Medio)

- **Dónde:** mockup 04, "Alternativas para la cena". §7.10 habla de un "panel lateral de **alternativas**". El *popover* cubre las columnas Mié y Jue, y deja textos truncados en "Arroz con p…" y "Tostadas co…".
- **Por qué importa:** la comparación entre la comida original y las alternativas exige ver el contexto del día, y el *popover* tapa justamente las comidas vecinas. Además, no coincide con el manual.
- **Solución:** implementar el panel lateral de §7.10 (*drawer* de 400 px a la derecha, con la comida actual arriba y 3 alternativas que muestren el delta de kcal y proteína respecto a la original), o actualizar §7.10 si se prefiere el *popover*, anclándolo sin tapar la columna del día.

#### D-13 · Paleta de base genérica frente a la promesa de "no de plantilla" (Medio)

- **Dónde:** §1.1 ("No es… genérica o de plantilla") frente a §2 y §6.8.
  - Neutros, estados y sombras coinciden exactamente con Untitled UI: `#101828`, `#344054`, `#475467`, `#EAECF0`, `#D0D5DD`, `#067647`, `#B54708`, `#B42318`, `#175CD3`, y la sombra `0 1px 2px rgba(16,24,40,.05)`.
  - La escala verde coincide con la escala *success* de Untitled UI.
  - El índigo es el de Tailwind (`#6366F1`, `#4F46E5`, `#4338CA`).
- **Por qué importa:** usar bases accesibles probadas es correcto, pero el combo "verde salud + violeta IA" es el aspecto por defecto de muchos SaaS con IA de 2024–2026, y el resultado se parece a una plantilla. Para un portfolio, la diferenciación visual cuenta.
- **Solución:** no hace falta cambiar los neutros. Basta con reforzar 2 o 3 rasgos propios y documentarlos en §1.4:
  - Que el motivo "línea de pulso" del isotipo aparezca en *sparklines*, en el progreso IA y en los estados vacíos.
  - El doble anillo energía/entreno como componente firma.
  - Usar con más presencia la Manrope 800 en las cifras.
  - Opcionalmente, ajustar el verde de marca a un tono propio, siempre comprobando los contrastes.

#### D-14 · "Comparar" sin efecto; faltan medias semanales y el reparto por comidas (Medio)

- **Dónde:** mockup 05.
  - El interruptor "Comparar" está activado, pero ningún gráfico muestra el periodo anterior; solo los deltas de los KPI.
  - HU-15 pide "energía y macros medios **por semana**", y el gráfico es diario.
  - El módulo F de la propuesta incluye el "reparto por comidas", que no aparece ni en §7.14 ni en el mockup.
- **Por qué importa:** un control que no produce un cambio visible confunde. Y las métricas comprometidas en la propuesta no aparecen.
- **Solución:**
  - Mostrar la serie del periodo anterior como línea gris discontinua en "Energía consumida vs. objetivo" cuando "Comparar" está activo.
  - Añadir un `ns-segmented` "Diario / Semanal" al gráfico.
  - Añadir a §7.14 una tarjeta pequeña "Reparto por comidas" (barras horizontales).

#### D-15 · Estados seleccionados con contraste casi nulo (Medio)

- **Dónde:** §5.4 `ns-segmented`: la opción activa usa `surface` y sombra `xs` sobre `surface-muted`, lo que da 1,10:1 entre fondos. Se ve en el 05 ("4 semanas") y en el 02 ("Cena").
- **Por qué importa:** WCAG 1.4.11 exige 3:1 en los indicadores visuales de estado. En los mockups funciona solo porque el texto activo va en negrita, pero el manual no lo exige.
- **Solución:** añadir a §5.4: opción activa con texto `text` 600 frente a `text-secondary` 500 en las inactivas, más un borde de 1 px `border-input`, o un subrayado inferior `green-700`.

#### D-16 · Funcionalidades de la propuesta sin pantalla definida (Medio)

- **Dónde:** al comparar §7 de DESIGN.md con §3 y §10 de PROJECT_PROPOSAL.md:
  - Las 17 pantallas cubren los módulos A–I a grandes rasgos.
  - No hay pantalla ni patrón para:
    - Lectura de etiqueta nutricional (OCR, HU-17, con el aviso de "valores incoherentes").
    - Escáner de código de barras (visor de cámara, sin resultado).
    - Recuperar contraseña y nueva contraseña (HU-03).
    - Catálogo o búsqueda de recetas (solo existe el detalle §7.11).
    - Registro de pesaje (HU-11; solo aparece el botón "+ Pesaje" en el 01).
  - §7.17 agrupa cuatro pantallas de administración en un solo apartado.
- **Por qué importa:** son flujos MVP o "Importante" sin definición visual. El desarrollador tendrá que improvisarlos y romperá la consistencia.
- **Solución:** añadir subapartados breves (objetivo, estructura, componentes y estados) para OCR, escáner, recuperación de contraseña y el modal de pesaje, y una línea en §7.6 o una pantalla "Recetas" para el catálogo.

#### D-17 · Microcopy (Bajo)

- **Dónde y solución:**
  - 01, badge "En línea" en "Energía de hoy": se puede leer como "online". Usar "En objetivo" o "Vas bien".
  - 03, "Detectado: «salmon fillet»": expone la salida cruda del modelo en inglés. Mostrar "Detectado como: filete de salmón" o moverlo a un *tooltip*.
  - 05, "Carbohidr.": usar "Carbohidratos" o "HC" (con su leyenda).
  - 05, en "Personalizado" el icono va pegado al texto: falta el `gap`.

#### D-18 · La cabecera y la franja pre-entreno no coinciden entre pantallas (Bajo)

- **Dónde:**
  - §7.4 sitúa "Registrar con foto" y "Añadir comida" en la cabecera del dashboard; el 01 las pone en la tarjeta "Comidas de hoy" y en la topbar.
  - §5.8 pone el avatar en la topbar; los mockups lo llevan a la sidebar.
  - La merienda pre-entreno es "Pre-entreno 17:30" en el 01, la franja "Merienda" con badge en el 04, y el asistente del 06 propone "Registrar a las 17:00".
- **Solución:** actualizar §7.4 y §5.8 a lo que muestran los mockups (es la mejor opción) y unificar la hora en 17:00.

#### D-19 · La regla de un solo primario verde choca con el CTA global de la topbar (Bajo)

- **Dónde:** §2.7 dice "Un solo botón primario verde por vista", pero la topbar siempre lleva "Registrar comida" en `primary`. En el 06 coincide con "Registrar a las 17:00", también `primary`, y lo mismo pasará en §7.6 con "Guardar comida".
- **Solución:** precisar en §2.7 que el CTA de la topbar no cuenta para la regla, o bajar las acciones contextuales del chat a `secondary`.

#### D-20 · El badge "Estimación IA" y el umbral de "Continuar manualmente" no están unificados (Bajo)

- **Dónde:**
  - §2.7 exige el badge "Estimación IA" en todo dato de IA no confirmado; en el 03 no aparece (solo un *pill* "Analizado en 7,8 s" y el *disclaimer*).
  - El botón "Continuar manualmente": §5.15 dice que es visible desde el segundo 10, HU-07 habla de 30 s y el mockup 02 lo muestra a los 6 s.
- **Solución:** añadir "Estimación IA" en la tarjeta "Total de la comida" del 03 y fijar un único criterio. Lo recomendable es que el botón sea visible siempre, como en el mockup, y actualizar §5.15.

#### D-21 · Rangos de fechas ligeramente incoherentes (Bajo)

- **Dónde:**
  - El 05 dice "25 oct – 21 nov" (28 días), pero los KPI cuentan 27 días y el mapa de calor empieza el 26 oct.
  - El gráfico de peso del 01 empieza el 24 oct.
- **Solución:** usar "26 oct – 21 nov" en el 05 y alinear el eje del peso del 01.

#### D-22 · La captura del 06 empieza con una tarjeta cortada (Bajo)

- **Dónde:** `png/06-asistente-ia.png`: la primera tarjeta enriquecida (tabla de proteína) aparece cortada bajo la cabecera del chat.
- **Solución:** desplazar el *scroll* inicial para que la conversación empiece en un mensaje completo, o añadir un separador "Hoy" arriba. Así se entiende que es *scroll* y no un error.

---

## 4. Aspectos correctamente resueltos

- **Contrastes documentados verificados:** he recalculado 30+ pares de §2 (p. ej. blanco/`green-700` 5,37, `text-tertiary`/blanco 5,40, `border-input` 3,63, estados 5,2–6,6, `ai-600`/`ai-50` 5,55) y todos son exactos. Es especialmente acertado crear `--ns-border-input` para cumplir 1.4.11 y advertir que `green-500` no sirve para texto.
- **Arquitectura de tokens en dos niveles** (primitivos → semánticos), con capas, movimiento y layout incluidos, y la regla Stylelint `color-no-hex`.
- **Guía Angular realista:** CDK en lugar de Material, `input()` con señales, `host` *bindings*, `BreakpointObserver` y tema ECharts derivado de los tokens.
- **Separación visual IA / dato confirmado** como principio de marca (§1.2), muy bien ejecutada en el 03: badges de confianza con texto y %, "Detectado como…", *disclaimer* y "Nada se guarda hasta que confirmes".
- **Pantalla de revisión (03) ejemplar:**
  - Marcadores numerados en la foto enlazados con la lista.
  - Alternativas en línea.
  - Unidades caseras (½ pieza ↔ 70 g).
  - Impacto en el día ("te quedarán 220 kcal").
  - CTA con el total ("Confirmar y guardar · 718 kcal").
- **Coherencia numérica entre pantallas:**
  - Sumas de comidas y macros del 01.
  - Totales del 03.
  - Media del 04 (2.523 kcal = media real de los 7 días).
  - Proteína en g/kg con el peso de 71,4 kg.
  - Carbohidratos −18 % = 63 g en el 05 y el 01.
  - El sábado del 04 coincide comida a comida con el 01.
- **Dashboard** con jerarquía clara: energía restante como cifra héroe, macros con "Te faltan…", comidas pendientes del plan con "Marcar comida", hueco vacío con sugerencia del plan.
- **Asistente** con transparencia: "Solo lectura de tus datos", chips "Datos consultados", "Escribiendo… / Detener", panel de contexto y aviso de que todo cambio requiere confirmación.
- **No se depende solo del color en los macros:** siempre hay etiqueta textual (P/C/G, nombres, %).
- **Avisos orientativos (`ns-disclaimer`)** presentes en todas las pantallas que los requieren.
- **Movimiento responsable:** las animaciones del escáner y del cursor están condicionadas a `prefers-reduced-motion: no-preference`.
- **Microcopy** en tono "tú", sin culpa ni juicios, con formatos españoles (coma decimal, punto de miles, 24 h).
- **Especificación de pantallas (§7)** sistemática: objetivo, estructura, componentes, acciones y responsive.

---

## 5. Mejoras prioritarias (top 10)

| # | Mejora | Problemas | Esfuerzo |
|---|---|---|---|
| 1 | Separar el color de actividad de la familia IA (p. ej. cian `#0E7490`) y retirar `badge--ai` de "Pre-entreno" | D-01 | Bajo |
| 2 | Corregir la incoherencia "sin lactosa / yogur griego" y unificar el estilo de los chips de restricción | D-06 | Bajo |
| 3 | Mínimo de 12 px real: escala §3.2, SVG de gráficos y planificador; arreglar el texto del mapa de calor y añadir un indicador no cromático | D-02, D-03 | Bajo-medio |
| 4 | Corregir los dos fallos de contraste del CSS (grupos de la sidebar → `ink-400`; buscador → `border-input`) y reforzar el estado activo de `ns-segmented` | D-09, D-15 | Bajo |
| 5 | Completar el flujo de foto: variante *dropzone* inicial, error con "Continuar manualmente", confianza baja y confirmación con *toast* | D-05, D-10, D-20 | Medio |
| 6 | Añadir 2–3 mockups móviles de 390 px (dashboard con bottom-nav, foto y revisión, planificador por día) | D-04 | Medio |
| 7 | Mockups de registro manual (07) y *onboarding*/login (08), más la variante vacía del dashboard | D-07 | Medio-alto |
| 8 | Sincronizar DESIGN.md con el CSS de los mockups (título de tarjeta, `lg`, iconos, cantidad, HEX de macros) | D-08, D-18, D-19 | Bajo |
| 9 | Planificador: carbohidratos por día, columnas de entreno diferenciadas, panel lateral de alternativas, textos ≥ 12 px | D-11, D-12 | Medio |
| 10 | Estadísticas: serie del periodo anterior con "Comparar", vista semanal y reparto por comidas; añadir a §7 las pantallas de OCR, escáner, recuperación y pesaje | D-14, D-16 | Medio |
