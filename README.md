# NutriSport AI · Backend

> **"Tu nutrición deportiva, impulsada por inteligencia artificial."**

NutriSport AI es una aplicación web que ayuda a deportistas a planificar, registrar y analizar su alimentación teniendo en cuenta sus objetivos físicos, sus hábitos alimentarios y su actividad deportiva. Este repositorio contiene la **API REST** (Spring Boot): cuentas y seguridad, perfil y objetivos nutricionales, catálogo de alimentos, registro de comidas (manual o con una foto analizada por IA), entrenamientos, seguimiento diario y administración.

Es el proyecto final individual de 2.º de DAW. La especificación completa está en [`docs/PROJECT_PROPOSAL.md`](docs/PROJECT_PROPOSAL.md).

> [!IMPORTANT]
> NutriSport AI ofrece **estimaciones orientativas** y **no sustituye el consejo de un profesional sanitario o dietista-nutricionista**. Los valores reconocidos en las fotos son estimaciones de IA, y los objetivos calculados tienen límites de seguridad, pero no son una prescripción.

---

## Índice

1. [Stack](#stack)
2. [Requisitos](#requisitos)
3. [Arranque paso a paso](#arranque-paso-a-paso)
4. [Perfiles y modo de IA](#perfiles-y-modo-de-ia)
5. [Tests](#tests)
6. [Estructura de paquetes](#estructura-de-paquetes)
7. [Migraciones Flyway](#migraciones-flyway)
8. [Despliegue con Docker Compose](#despliegue-con-docker-compose)
9. [Documentación](#documentación)
10. [Decisiones de implementación](#decisiones-de-implementación)
11. [Datos y atribuciones](#datos-y-atribuciones)
12. [Licencia](#licencia)

---

## Stack

| Área | Tecnología |
|---|---|
| Lenguaje y framework | Java 21, Spring Boot 4.2 (Spring Web MVC, Data JPA, Validation, Security, Mail, Actuator), Maven Wrapper |
| Base de datos | PostgreSQL 16 con Flyway; búsqueda por similitud con `pg_trgm` |
| Seguridad | JWT con jjwt 0.12 (HS256) + refresh token rotatorio en cookie HttpOnly; BCrypt (coste 12); limitación de peticiones con Bucket4j y Caffeine |
| Inteligencia artificial | Spring AI 2.1 con Anthropic (Claude), detrás de puertos propios y con modo simulado |
| Almacenamiento de fotos | MinIO (compatible con S3) con AWS SDK v2 y URLs prefirmadas |
| Mapeo y documentación | MapStruct 1.6, springdoc-openapi 3.2 (OpenAPI y Swagger UI) |
| Tests | JUnit 5, Spring Boot Test, MockMvc y Testcontainers (PostgreSQL y MinIO reales) |
| Entorno local | Docker Compose: PostgreSQL, MinIO y Mailpit |

## Requisitos

- **JDK 21** (por ejemplo, Eclipse Temurin 21). No hace falta instalar Maven: se usa `./mvnw`.
- **Docker Desktop** en marcha: lo usan los servicios de desarrollo y los tests (Testcontainers).
- **Git Bash** en Windows (o cualquier terminal POSIX en Linux o macOS). Los comandos de este README están escritos para ella.

## Arranque paso a paso

Todos los comandos se ejecutan desde la raíz del repositorio.

**1. Crea el fichero de variables de entorno**

```bash
cp .env.example .env
```

Edita `.env` y pon valores propios. El fichero no se sube a Git (está en `.gitignore`).

| Variable | Para qué sirve |
|---|---|
| `DB_USER`, `DB_PASS` | Usuario y contraseña de PostgreSQL |
| `JWT_SECRET` | Secreto para firmar los JWT. **Mínimo 32 bytes**; genera uno con `openssl rand -base64 48` |
| `MINIO_USER`, `MINIO_PASS` | Credenciales de MinIO (también para entrar en su consola) |
| `ADMIN_EMAIL`, `ADMIN_PASS` | Administrador que se crea al arrancar con el perfil `local` |
| `IA_API_KEY` | Clave de la API de Anthropic. Opcional: sin ella la IA funciona en modo simulado |

**2. Levanta los servicios de desarrollo**

```bash
docker compose up -d
```

Arranca PostgreSQL (puerto **5433** del ordenador), MinIO (API en 9000 y consola en 9001) y Mailpit (SMTP en 1025 e interfaz web en 8025). Docker Compose lee `.env` automáticamente.

**3. Carga de las variables**

No hay que exportarlas: la aplicación lee `.env` de la raíz al arrancar (`spring.config.import=optional:file:.env[.properties]`). Si falta el fichero, usa las variables de entorno del sistema, que es lo que ocurre en Docker y en CI. Si prefieres tenerlas también en la terminal (por ejemplo, para otras herramientas):

```bash
set -a; source .env; set +a
```

**4. Arranca la API con el perfil `local`**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Al arrancar, Flyway crea o actualiza el esquema y la aplicación crea el bucket privado de fotos si no existe. Con el perfil `local` se crean además el administrador de `ADMIN_EMAIL` y un catálogo de alimentos de desarrollo (ver [Datos y atribuciones](#datos-y-atribuciones)).

**5. Pruébala**

| Herramienta | URL | Notas |
|---|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html | Solo con el perfil `local`. Haz login en `POST /auth/login`, copia el `accessToken` y pulsa **Authorize** |
| Estado de la API | http://localhost:8080/actuator/health | Público; responde `{"status":"UP"}` |
| Mailpit | http://localhost:8025 | Aquí llegan los correos de verificación y de recuperación de contraseña |
| Consola de MinIO | http://localhost:9001 | Entra con `MINIO_USER` y `MINIO_PASS`. Las fotos están en el bucket privado `nutrisport-fotos` |

Recorrido rápido: `POST /auth/registro` → abre el correo en Mailpit y copia el token del enlace → `POST /auth/verificar-email` → `POST /auth/login` → `PUT /perfil` → `GET /seguimiento/resumen`.

## Perfiles y modo de IA

| Perfil | Fichero | Uso |
|---|---|---|
| *(ninguno)* | `application.properties` | Valores por defecto, pensados para producción: cookie `Secure`, Swagger y OpenAPI desactivados, Actuator solo con `health` |
| `local` | `application-local.properties` | Desarrollo: Swagger UI activo, cookie sin `Secure` (HTTP), logs `DEBUG` de la aplicación, administrador y catálogo de desarrollo |
| `test` | `src/test/resources/application-test.properties` | Solo tests: secreto JWT público de prueba, IA simulada, tareas programadas desactivadas y procesado de fotos síncrono |

**Modo de IA** (`app.ia.modo`):

- `simulado` (por defecto): no llama a ningún proveedor ni necesita clave. El análisis de una foto devuelve siempre los mismos alimentos de ejemplo. Es el modo de los tests y del desarrollo diario.
- `real`: usa Claude a través de Spring AI. Necesita `IA_API_KEY` en `.env`; el modelo se puede cambiar con `IA_MODELO`. Para activarlo sin tocar ficheros:

  ```bash
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--app.ia.modo=real
  ```

En los dos modos se aplican el límite diario de análisis por usuario (`app.ia.limite-analisis-diario`), la verificación del email y el registro de uso y coste estimado en `uso_ia`. Al proveedor solo se envían la foto sin metadatos EXIF, el tipo de comida y la descripción opcional del usuario; nunca el nombre, el email ni identificadores.

## Tests

```bash
./mvnw test
```

Los tests de integración necesitan **Docker en marcha**: Testcontainers arranca un PostgreSQL 16 y un MinIO reales que comparten todas las clases. No usan los servicios de `docker compose` ni el fichero `.env`.

- Una sola clase: `./mvnw test -Dtest=FlujoCompletoTest`.
- `FlujoCompletoTest` recorre el MVP de principio a fin solo con la API HTTP: registro, perfil, comidas, foto analizada y confirmada, entrenamiento, resumen, consentimiento y eliminación de la cuenta.
- Si fallan casi todos los tests de integración con errores del tipo `NoClassDefFoundError` tras editar en VS Code, el compilador del IDE ha dejado clases inconsistentes en `target/`: ejecuta `./mvnw clean test`.

## Estructura de paquetes

El código está organizado por módulo funcional en `com.nutrisport`. Dentro de cada módulo, las capas siguen siempre los mismos nombres: `web` (controladores), `dto`, `servicio`, `repositorio`, `entidad` y `mapper` (MapStruct).

| Paquete | Contenido |
|---|---|
| `auth` | Registro, verificación de email, login, refresh tokens, recuperación de contraseña, cuenta y su eliminación; filtro JWT y seguridad |
| `perfil` | Perfil deportivo, cálculo de objetivos, objetivos diarios y preferencias y restricciones alimentarias |
| `catalogo` | Alimentos, categorías, búsqueda tolerante a errores, cálculo nutricional e importación CSV |
| `comidas` | Registro de comidas, conversión de unidades y análisis de fotos con IA (procesado asíncrono y confirmación) |
| `actividad` | Entrenamientos, tipos de actividad (MET) y cálculo del gasto |
| `seguimiento` | Resumen diario y registro y serie de peso |
| `admin` | Gestión del catálogo, categorías y usuarios por un administrador, con auditoría |
| `ia` | Puertos de IA, adaptadores de Spring AI, adaptadores simulados y control de límites y costes |
| `almacenamiento` | Subida, validación y reprocesado de fotos (sin EXIF), MinIO/S3 y URLs prefirmadas |
| `comun` | Configuración (seguridad, OpenAPI, asincronía, tareas programadas), errores Problem Details, límites de peticiones, validaciones y utilidades |

## Migraciones Flyway

Las migraciones están en `src/main/resources/db/migration`. **Una migración ya fusionada en `main` no se modifica nunca**: cualquier cambio va en la siguiente versión libre.

**MVP**

| Versión | Contenido |
|---|---|
| `V1` | Usuarios, roles y tokens de un solo uso |
| `V2` | Refresh tokens persistentes con familia y rotación |
| `V3` | Perfil deportivo y preferencias alimentarias |
| `V4` | Catálogo de alimentos y categorías (con índice de trigramas) |
| `V5` | Comidas y alimentos registrados |
| `V6` | Tipos de actividad, entrenamientos y objetivos diarios |
| `V7` | Registro de peso |
| `V8` | Uso de la IA (límites y costes) |
| `V9` | Análisis de imágenes |
| `V10` | Auditoría de acciones administrativas |

**Ampliación (previstas):** `V11` fuentes externas, `V12` recetas, `V13` mapeo de ingredientes, `V14` menús, `V15` lista de la compra, `V16` insights y `V17` asistente. Van una versión por detrás de lo que indica la guía, porque `V10` la ocupa la auditoría del MVP.

**Repetibles:** `R__categorias.sql` (categorías iniciales) y `R__tipos_actividad.sql` (catálogo de actividades con su MET). Flyway las vuelve a aplicar cada vez que cambia su contenido.

## Despliegue con Docker Compose

El `Dockerfile` de la raíz construye la API en dos etapas: compila con Maven y Temurin 21 y la ejecuta en `eclipse-temurin:21-jre-alpine`, con un usuario sin privilegios y un *healthcheck* sobre `/actuator/health`. Para levantar la API en un contenedor junto a los servicios:

```bash
docker compose --profile app up -d --build
```

El servicio `backend` solo arranca con el perfil `app`, así que `docker compose up -d` sigue levantando únicamente los servicios de desarrollo. Usa el mismo `.env` y está configurado para una **demostración local**: perfil `local` y API en http://localhost:8080. Para una puesta en producción hay que, como mínimo:

- quitar el perfil `local`, para que Swagger quede desactivado y la cookie sea `Secure`;
- servirla con HTTPS detrás de un proxy inverso que sustituya `X-Forwarded-For` y `X-Forwarded-Proto` (la aplicación usa `server.forward-headers-strategy=framework`), sin exponer el puerto 8080;
- usar secretos propios, un almacenamiento S3 gestionado y un servidor de correo real.

## Documentación

- [`docs/PROJECT_PROPOSAL.md`](docs/PROJECT_PROPOSAL.md): especificación funcional y técnica (módulos, arquitectura, base de datos, API, seguridad y privacidad).
- [`docs/DESIGN.md`](docs/DESIGN.md): identidad visual y sistema de diseño del frontend.
- [`docs/Guia_Github_Backend_NutriSport.md`](docs/Guia_Github_Backend_NutriSport.md): guía de desarrollo paso a paso del backend (issues, ramas y pruebas de cada paso).
- [`docs/API_INTEGRATIONS.md`](docs/API_INTEGRATIONS.md): integración con APIs externas de alimentos y recetas (Ampliación).
- **Swagger UI** con el perfil `local`: todos los endpoints agrupados por módulo, con sus errores en formato Problem Details y ejemplos.

## Decisiones de implementación

Concretan o modifican lo que dice la propuesta (Annex B de la guía):

1. **Búsqueda sin `unaccent`:** se guarda `nombre_normalizado` (minúsculas y sin tildes, calculado en Java) con un índice GIN de trigramas.
2. **UUID** generados por Hibernate con `@UuidGenerator(style = TIME)` (ordenables por tiempo) en lugar de UUID v7 estricto.
3. **Factores de actividad bajos (1,2–1,5):** solo reflejan la actividad fuera del entrenamiento, que se suma aparte por MET; ese gasto extra va a carbohidratos.
4. **Objetivos de días pasados guardados** en `objetivo_diario`: un cambio de perfil solo recalcula hoy y los días futuros.
5. **Fotos JPEG y PNG** en el MVP; el backend las recodifica siempre a JPEG, lo que elimina los metadatos EXIF.
6. **Estado del análisis por *polling*** (sin SSE). El *streaming* del asistente queda para la Visión.
7. **Sin Resilience4j:** *timeout* y un único reintento en las llamadas a la IA.
8. **Registro con un email existente:** responde 202 igual que un registro nuevo y avisa por correo al titular (protección contra la enumeración de cuentas).
9. **Login sin verificar el email:** se permite; las funciones de IA exigen la verificación (`EMAIL_NO_VERIFICADO`).
10. **Tablas y columnas añadidas** respecto a la propuesta: `usuario_rol`, `tipo_actividad`, `objetivo_diario`, `uso_ia`, `auditoria`, `categoria.seccion_compra`, `usuario.eliminado_en` y `refresh_token.familia` y `reemplazado_por`, entre otras.
11. **Endpoint añadido:** `DELETE /perfil/objetivos/manuales` (volver al cálculo automático).
12. **Catálogo de desarrollo no oficial** (ver más abajo).
13. **Lactosa como intolerancia** (`contieneLactosa`), no como dieta; `HALAL` y `KOSHER` no se filtran automáticamente.
14. **Límite de peticiones en memoria** (Bucket4j + Caffeine): válido solo con **una instancia** de la API.
15. **Eliminación de cuenta diferida 30 días** o inmediata, a elección del usuario. La reactivación de una cuenta eliminada por un administrador está prevista, pero **aún no está implementada**: hoy una cuenta `ELIMINADA` no se puede modificar.
16. **PostgreSQL en el puerto 5433** del ordenador, para convivir con otro PostgreSQL local.

## Datos y atribuciones

> [!WARNING]
> **Los datos de desarrollo no son oficiales.** El catálogo que se carga con el perfil `local` (`src/main/resources/datos/alimentos_desarrollo.csv`) tiene valores nutricionales **aproximados**, generados para poder probar la aplicación (`fuente = ADMIN`, `referencia_externa = DEV`). No deben usarse para tomar decisiones de alimentación. Antes de un uso real hay que importar una fuente oficial (por ejemplo, BEDCA o USDA) con el importador CSV de administración.

Fuentes externas de la Ampliación (ver [`docs/API_INTEGRATIONS.md`](docs/API_INTEGRATIONS.md)). **Todavía no están implementadas**; cuando lo estén, se atribuirán así:

| Fuente | Licencia | Atribución |
|---|---|---|
| [Open Food Facts](https://world.openfoodfacts.org) | Base de datos **ODbL**, contenidos DbCL, imágenes CC BY-SA | "Datos de Open Food Facts (ODbL) · openfoodfacts.org", con enlace. Sus productos se guardan separados del resto para poder compartir esa parte bajo ODbL si se publicara |
| [TheMealDB](https://www.themealdb.com) | Términos de uso propios | "Receta e imagen: TheMealDB (themealdb.com)", con enlace; publicar en una tienda de apps requiere ser *supporter* |
| [USDA FoodData Central](https://fdc.nal.usda.gov) | Dominio público (CC0 1.0) | "U.S. Department of Agriculture, Agricultural Research Service. FoodData Central" |

## Licencia

Distribuido bajo la [licencia MIT](LICENSE).
