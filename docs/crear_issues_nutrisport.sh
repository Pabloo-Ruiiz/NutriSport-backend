#!/usr/bin/env bash
# Crea las issues de la guía docs/Guia_Github_Backend_NutriSport.md en orden (requiere GitHub CLI y gh auth login).
# Ejecútalo desde la raíz del repositorio: bash docs/crear_issues_nutrisport.sh
set -euo pipefail

if ! command -v gh >/dev/null 2>&1; then echo "Falta GitHub CLI (gh)"; exit 1; fi
gh auth status >/dev/null

echo "Se van a crear 37 issues en: $(gh repo view --json nameWithOwner -q .nameWithOwner)"
read -r -p "¿Continuar? (s/N) " r; [ "$r" = "s" ] || exit 0

echo "Creando issue 1/37..."
gh issue create --title "[Backend] Subir la documentación del proyecto a docs/" --body-file - <<'FIN_ISSUE_01'
## Objetivo
Tener en el repositorio la documentación que usarán todos los pasos: la propuesta funcional y técnica, el manual de diseño, los mockups y esta guía.

## Tareas
- [ ] Comprobar que `docs/` contiene `PROJECT_PROPOSAL.md`, `DESIGN.md`, `API_INTEGRATIONS.md`, `review/`, `design/` (mockups y logotipos), esta guía y `crear_issues_nutrisport.sh`
- [ ] Revisar que los enlaces de los documentos funcionan desde GitHub
- [ ] Subir `docs/` a `main` mediante su PR

## Criterios de aceptación
- [ ] En GitHub se ven `docs/PROJECT_PROPOSAL.md` y `docs/DESIGN.md` con los diagramas Mermaid renderizados
- [ ] `docs/design/mockups/png/` contiene las 6 capturas

## Depende de
Ninguna (es la primera issue).

## Guía
Paso 1 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_01

echo "Creando issue 2/37..."
gh issue create --title "[Backend] Crear el proyecto Spring Boot (en la raíz del repositorio)" --body-file - <<'FIN_ISSUE_02'
## Objetivo
Tener el proyecto Spring Boot creado en la raíz del repositorio, con las dependencias base, un `.gitignore` completo y las instrucciones de contexto para el asistente de IA.

## Tareas
- [ ] Generar el proyecto con Spring Initializr (Maven, Java 21, `com.nutrisport`, artifact `nutrisport-backend`) en la raíz del repositorio, junto a `docs/`
- [ ] Completar el `.gitignore` de la raíz
- [ ] Crear `.github/copilot-instructions.md` con el Prompt 0
- [ ] Comprobar que el proyecto compila

## Criterios de aceptación
- [ ] `./mvnw -q compile` termina sin errores
- [ ] Existe `src/main/java/com/nutrisport/NutriSportApplication.java`
- [ ] `git status` no muestra `target/` ni `.env`

## Clases y ficheros
- `com.nutrisport`: `NutriSportApplication`
- recursos: `.gitignore (raíz)`, `.github/copilot-instructions.md`

## Depende de
#1

## Guía
Paso 2 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_02

echo "Creando issue 3/37..."
gh issue create --title "[Backend] Entorno de desarrollo con Docker Compose (PostgreSQL, MinIO y Mailpit)" --body-file - <<'FIN_ISSUE_03'
## Objetivo
Levantar con un solo comando los servicios que necesita el backend en local: PostgreSQL 16, almacenamiento de objetos MinIO (compatible con S3) y Mailpit para capturar correos.

## Tareas
- [ ] Crear `docker-compose.yml` con `postgres`, `minio` y `mailpit`
- [ ] Crear `.env.example` (se sube) y `.env` (no se sube) con las variables
- [ ] Levantar los servicios y comprobar que están sanos

## Criterios de aceptación
- [ ] `docker compose ps` muestra los tres servicios en marcha y `postgres` como *healthy*
- [ ] Mailpit responde en http://localhost:8025 y la consola de MinIO en http://localhost:9001
- [ ] `.env` no aparece en `git status`

## Clases y ficheros
- recursos: `docker-compose.yml`, `.env.example`, `.env (no se sube a Git)`

## Depende de
#2

## Guía
Paso 3 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_03

echo "Creando issue 4/37..."
gh issue create --title "[Backend] Configuración base: PostgreSQL, Flyway, JWT, OpenAPI, MapStruct y tests con Testcontainers" --body-file - <<'FIN_ISSUE_04'
## Objetivo
Añadir las dependencias comunes, configurar la conexión a PostgreSQL y Flyway, crear las clases de configuración y dejar preparada la base de los tests de integración con Testcontainers.

## Tareas
- [ ] Añadir `jjwt`, `springdoc-openapi` y MapStruct (con su *annotation processor*) al `pom.xml`
- [ ] Crear `application.properties`, `application-local.properties` y `application-test.properties`
- [ ] Crear `JwtProperties` y `OpenApiConfig`
- [ ] Crear `IntegracionBase` (PostgreSQL con Testcontainers) y `ArranqueAplicacionTest`; borrar el test de Initializr
- [ ] Crear `.vscode/launch.json` que lea `.env`

## Criterios de aceptación
- [ ] Con Docker en marcha, `./mvnw test` pasa
- [ ] La aplicación arranca con el perfil `local` y en el log aparece `Started NutriSportApplication`
- [ ] No hay contraseñas escritas en el código ni en los `.properties`

## Clases y ficheros
- `comun.config`: `JwtProperties`, `OpenApiConfig`
- pruebas: `IntegracionBase`, `ArranqueAplicacionTest`
- recursos: `src/main/resources/application.properties`, `src/main/resources/application-local.properties`, `src/test/resources/application-test.properties`, `.vscode/launch.json (no se sube a Git)`

## Depende de
#3

## Guía
Paso 4 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_04

echo "Creando issue 5/37..."
gh issue create --title "[Backend] Errores con Problem Details (RFC 9457), DTO comunes y entidad base" --body-file - <<'FIN_ISSUE_05'
## Objetivo
Tener el formato de errores de la API (Problem Details, RFC 9457), las excepciones que usarán todos los servicios, la respuesta paginada y la entidad base con UUID y fechas de auditoría.

## Tareas
- [ ] Crear `EntidadBase` (UUID, `creadoEn`, `actualizadoEn`)
- [ ] Crear `ErrorCampo` y `PaginaResponse`
- [ ] Crear `ApiException` y sus subclases
- [ ] Crear `ManejadorGlobalExcepciones` y su test

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] Un error de validación devuelve 400 con `type`, `title`, `status`, `detail`, `codigo` y `errors[]`
- [ ] Un error inesperado devuelve 500 sin traza en el cuerpo

## Clases y ficheros
- `comun.entidad`: `EntidadBase`
- `comun.dto`: `ErrorCampo`, `PaginaResponse`
- `comun.excepcion`: `ApiException`, `PeticionInvalidaException`, `RecursoNoEncontradoException`, `AccesoDenegadoException`, `ConflictoException`, `ReglaNegocioException`, `ArchivoNoValidoException`, `LimiteExcedidoException`, `ServicioNoDisponibleException`, `ManejadorGlobalExcepciones`
- pruebas: `comun.ManejadorGlobalExcepcionesTest`

## Depende de
#4

## Guía
Paso 5 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_05

echo "Creando issue 6/37..."
gh issue create --title "[Backend] Migración V1 y entidades de usuarios, roles y tokens" --body-file - <<'FIN_ISSUE_06'
## Objetivo
Crear con Flyway las tablas de usuarios, roles, refresh tokens y tokens de un solo uso, y sus entidades y repositorios.

## Tareas
- [ ] Crear la migración `V1__usuarios_y_tokens.sql` (incluye la extensión `pg_trgm`)
- [ ] Crear los enums `Rol`, `EstadoUsuario` y `TipoToken`
- [ ] Crear las entidades `Usuario`, `RefreshToken` y `TokenUsuario` y sus repositorios
- [ ] Crear `UsuarioRepositorioTest`

## Criterios de aceptación
- [ ] La aplicación arranca y Flyway aplica `V1`
- [ ] `./mvnw test` pasa (Hibernate valida las entidades contra la migración)
- [ ] `\d usuario` en PostgreSQL muestra las restricciones `CHECK` de `estado`

## Clases y ficheros
- `auth.entidad`: `Usuario`, `RefreshToken`, `TokenUsuario`, `Rol`, `EstadoUsuario`, `TipoToken`
- `auth.repositorio`: `UsuarioRepositorio`, `RefreshTokenRepositorio`, `TokenUsuarioRepositorio`
- pruebas: `auth.UsuarioRepositorioTest`
- recursos: `db/migration/V1__usuarios_y_tokens.sql`

## Depende de
#5

## Guía
Paso 6 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_06

echo "Creando issue 7/37..."
gh issue create --title "[Backend] Usuario autenticado, servicio JWT, filtro y configuración de Spring Security" --body-file - <<'FIN_ISSUE_07'
## Objetivo
Proteger la API con JWT de corta duración: generación y validación de tokens, filtro de autenticación, rutas públicas, roles, CORS, cabeceras de seguridad y errores 401/403 en formato Problem Details.

## Tareas
- [ ] Crear `UsuarioAutenticado`, `UsuarioDetailsServiceImpl`, `JwtServicio`, `FiltroJwt` y `SeguridadUtils`
- [ ] Crear `PuntoEntradaNoAutenticado` y `ManejadorAccesoDenegado`
- [ ] Crear `SeguridadConfig` (stateless, CORS, rutas públicas, cabeceras, BCrypt 12)
- [ ] Crear `JwtServicioTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] `GET /api/v1/cuenta` sin token devuelve 401 en JSON Problem Details
- [ ] Swagger UI y `/actuator/health` son accesibles sin token

## Clases y ficheros
- `auth.seguridad`: `UsuarioAutenticado`, `UsuarioDetailsServiceImpl`, `JwtServicio`, `FiltroJwt`, `SeguridadUtils`, `PuntoEntradaNoAutenticado`, `ManejadorAccesoDenegado`
- `comun.config`: `SeguridadConfig`
- pruebas: `auth.JwtServicioTest`

## Depende de
#6

## Guía
Paso 7 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_07

echo "Creando issue 8/37..."
gh issue create --title "[Backend] Registro con consentimiento, verificación de email y login con refresh token" --body-file - <<'FIN_ISSUE_08'
## Objetivo
Permitir que un deportista cree su cuenta con consentimiento explícito de datos de salud, verifique su email (Mailpit en local) e inicie sesión obteniendo un access token y un refresh token en cookie `HttpOnly`.

## Tareas
- [ ] Crear la validación `@ContrasenaSegura` y la lista `contrasenas-comunes.txt`
- [ ] Crear `Tokens` (aleatorio y SHA-256), `CorreoServicio` y `CorreoServicioSmtp`
- [ ] Crear los DTO, `CuentaMapper`, `TokenUsuarioServicio`, `RefreshTokenServicio`, `AuthServicio`, `CookieRefresh` y `AuthController`
- [ ] Crear `AuthControllerTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] Registro → email en Mailpit → verificar → login devuelve `accessToken` y `Set-Cookie` con `HttpOnly` y `SameSite=Strict`
- [ ] Registrar un email existente responde igual (202) y no crea otra cuenta
- [ ] Una contraseña de la lista común devuelve 400 con el detalle del campo

## Clases y ficheros
- `comun.util`: `Tokens`
- `comun.validacion`: `ContrasenaSegura`, `ContrasenaSeguraValidator`
- `comun.correo`: `CorreoServicio`, `CorreoServicioSmtp`
- `auth.dto`: `RegistroRequest`, `LoginRequest`, `VerificarEmailRequest`, `ReenviarVerificacionRequest`, `TokenResponse`, `CuentaResponse`
- `auth.mapper`: `CuentaMapper`
- `auth.servicio`: `TokenUsuarioServicio`, `RefreshTokenServicio`, `AuthServicio`
- `auth.web`: `CookieRefresh`, `AuthController`
- pruebas: `auth.AuthControllerTest`
- recursos: `src/main/resources/contrasenas-comunes.txt`

## Depende de
#7

## Guía
Paso 8 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_08

echo "Creando issue 9/37..."
gh issue create --title "[Backend] Refresh token rotatorio, logout, recuperación de contraseña y límite de intentos" --body-file - <<'FIN_ISSUE_09'
## Objetivo
Completar el ciclo de sesión: renovar el access token con rotación y detección de reutilización, cerrar sesión, recuperar la contraseña por email y limitar los intentos de login, registro y recuperación.

## Tareas
- [ ] Añadir Bucket4j y crear `LimitadorPeticiones` y `PlanLimite`
- [ ] Añadir a `RefreshTokenServicio` la rotación, la revocación y la detección de reutilización
- [ ] Añadir `POST /auth/refresh`, `/logout`, `/logout-todas`, `/recuperar-password` y `/restablecer-password`
- [ ] Crear `RefreshTokenTest`, `RecuperacionPasswordTest` y `LimiteLoginTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] Reutilizar un refresh token ya rotado revoca toda su familia (401)
- [ ] El sexto intento fallido de login en 15 minutos devuelve 429 con `Retry-After`
- [ ] `POST /auth/recuperar-password` responde 202 tanto si el email existe como si no

## Clases y ficheros
- `comun.limite`: `LimitadorPeticiones`, `PlanLimite`
- `auth.dto`: `RecuperarPasswordRequest`, `RestablecerPasswordRequest`
- pruebas: `auth.RefreshTokenTest`, `auth.RecuperacionPasswordTest`, `auth.LimiteLoginTest`

## Depende de
#8

## Guía
Paso 9 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_09

echo "Creando issue 10/37..."
gh issue create --title "[Backend] Gestión de cuenta: datos, contraseña, email, consentimiento de salud y eliminación de cuenta" --body-file - <<'FIN_ISSUE_10'
## Objetivo
Que el usuario consulte y edite su cuenta, cambie la contraseña y el email, revoque o vuelva a dar el consentimiento de datos de salud y elimine su cuenta (borrado diferido de 30 días o inmediato); y disponer de un administrador en el perfil `local`.

## Tareas
- [ ] Crear `ActualizarCuentaRequest`, `CambiarPasswordRequest`, `CambiarEmailRequest` y `EliminarCuentaRequest`
- [ ] Crear `CuentaServicio`, `EliminacionCuentaServicio`, `PurgaCuentasTarea` y `CuentaController`
- [ ] Crear `InterceptorConsentimiento` (403 `CONSENTIMIENTO_REVOCADO` en las rutas de datos de salud)
- [ ] Crear `InicializadorAdmin` (solo perfil `local`) y `CuentaControllerTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] Cambiar la contraseña exige la actual y revoca los refresh tokens
- [ ] Tras revocar el consentimiento, `GET /api/v1/perfil` devuelve 403 `CONSENTIMIENTO_REVOCADO`; tras volver a darlo, 200
- [ ] `DELETE /cuenta` con la contraseña cierra las sesiones y el login posterior da 401
- [ ] Al arrancar con el perfil `local` existe el usuario de `ADMIN_EMAIL` con rol `ADMIN`

## Clases y ficheros
- `auth.dto`: `ActualizarCuentaRequest`, `CambiarPasswordRequest`, `CambiarEmailRequest`, `EliminarCuentaRequest`
- `auth.servicio`: `CuentaServicio`, `EliminacionCuentaServicio`, `PurgaCuentasTarea`
- `auth.web`: `CuentaController`
- `comun.config`: `InicializadorAdmin`, `InterceptorConsentimiento`, `WebConfig`
- pruebas: `auth.CuentaControllerTest`

## Depende de
#9

## Guía
Paso 10 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_10

echo "Creando issue 11/37..."
gh issue create --title "[Backend] Perfil deportivo y preferencias alimentarias (migración V2)" --body-file - <<'FIN_ISSUE_11'
## Objetivo
Guardar los datos físicos, deportivos y de objetivo del deportista y sus alergias, intolerancias, dietas, rechazos y favoritos.

## Tareas
- [ ] Crear la migración `V2__perfil_y_preferencias.sql`
- [ ] Crear los enums, las entidades `PerfilDeportivo` y `PreferenciaAlimentaria` y sus repositorios
- [ ] Crear la validación `@MayorDeEdad`, los DTO, `PerfilMapper`, `PerfilServicio` y `PerfilController`
- [ ] Crear `PerfilControllerTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] `PUT /perfil` con altura 300 cm devuelve 400 con el campo `alturaCm`
- [ ] Un menor de 18 años no puede guardar el perfil
- [ ] `PUT /perfil/preferencias` sustituye la lista completa

## Clases y ficheros
- `perfil.entidad`: `PerfilDeportivo`, `PreferenciaAlimentaria`, `Sexo`, `ObjetivoDeportivo`, `NivelActividad`, `TipoPreferencia`, `Alergeno`, `Intolerancia`, `Dieta`, `Severidad`
- `perfil.repositorio`: `PerfilDeportivoRepositorio`, `PreferenciaAlimentariaRepositorio`
- `comun.validacion`: `MayorDeEdad`, `MayorDeEdadValidator`
- `perfil.dto`: `PerfilRequest`, `PerfilResponse`, `PreferenciaDto`, `PreferenciasRequest`
- `perfil.mapper`: `PerfilMapper`
- `perfil.servicio`: `PerfilServicio`
- `perfil.web`: `PerfilController`
- pruebas: `perfil.PerfilControllerTest`
- recursos: `db/migration/V2__perfil_y_preferencias.sql`

## Depende de
#10

## Guía
Paso 11 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_11

echo "Creando issue 12/37..."
gh issue create --title "[Backend] Cálculo de objetivos diarios (Mifflin-St Jeor) y objetivos manuales" --body-file - <<'FIN_ISSUE_12'
## Objetivo
Calcular automáticamente la energía y los macronutrientes diarios a partir del perfil, con límites de seguridad, y permitir fijar objetivos manuales.

## Tareas
- [ ] Crear `CalculadoraObjetivos` (pura, sin base de datos)
- [ ] Recalcular los objetivos al guardar el perfil (salvo si son manuales)
- [ ] Crear `GET`/`PUT /perfil/objetivos` y `DELETE /perfil/objetivos/manuales`
- [ ] Crear `CalculadoraObjetivosTest` con los casos de la guía

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] Hombre, 30 años, 70 kg, 175 cm, `MODERADO`, `MANTENER` → 2308 kcal · 98 g P · 335 g HC · 64 g G
- [ ] Con `PERDER_GRASA` la energía baja un 15 % (1962 kcal) y la proteína sube a 2,0 g/kg
- [ ] La energía calculada nunca baja de 1500 kcal (hombre) / 1200 kcal (mujer)

## Clases y ficheros
- `comun.config`: `RelojConfig`
- `perfil.servicio`: `CalculadoraObjetivos`
- `perfil.dto`: `ObjetivosCalculados`, `ObjetivosResponse`, `ObjetivosManualesRequest`
- pruebas: `perfil.CalculadoraObjetivosTest`

## Depende de
#11

## Guía
Paso 12 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_12

echo "Creando issue 13/37..."
gh issue create --title "[Backend] Categorías y alimentos con búsqueda tolerante a errores (migración V3)" --body-file - <<'FIN_ISSUE_13'
## Objetivo
Crear el catálogo de categorías y alimentos con trazabilidad de la fuente de cada valor nutricional y una búsqueda que tolere tildes y errores de escritura.

## Tareas
- [ ] Crear `V3__catalogo_alimentos.sql` (índice GIN de trigramas y FK pendientes de V2) y `R__categorias.sql`
- [ ] Crear `TextoNormalizado`, enums, entidades `Categoria` y `Alimento`, `PorcionCasera` y repositorios
- [ ] Crear los DTO, `AlimentoMapper`, `AlimentoServicio`, `CategoriaServicio`, `AlimentoController` y `CategoriaController`
- [ ] Crear `AlimentoBusquedaTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] Buscar `platno` devuelve `Plátano` entre los primeros resultados
- [ ] Un usuario no ve los alimentos privados de otro (404)
- [ ] `GET /categorias?tipo=ALIMENTO` devuelve las categorías iniciales

## Clases y ficheros
- `comun.util`: `TextoNormalizado`
- `catalogo.entidad`: `Categoria`, `Alimento`, `PorcionCasera`, `TipoCategoria`, `FuenteDato`, `UnidadBase`, `Visibilidad`, `Preparacion`
- `catalogo.repositorio`: `CategoriaRepositorio`, `AlimentoRepositorio`
- `catalogo.dto`: `AlimentoRequest`, `AlimentoResponse`, `AlimentoResumenResponse`, `CategoriaResponse`
- `catalogo.mapper`: `AlimentoMapper`, `CategoriaMapper`
- `catalogo.servicio`: `AlimentoServicio`, `CategoriaServicio`
- `catalogo.web`: `AlimentoController`, `CategoriaController`
- pruebas: `catalogo.AlimentoBusquedaTest`
- recursos: `db/migration/V3__catalogo_alimentos.sql`, `db/migration/R__categorias.sql`

## Depende de
#12

## Guía
Paso 13 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_13

echo "Creando issue 14/37..."
gh issue create --title "[Backend] Importador CSV de alimentos y catálogo semilla de desarrollo" --body-file - <<'FIN_ISSUE_14'
## Objetivo
Poder cargar alimentos desde un CSV y tener un catálogo semilla de desarrollo (200–300 alimentos genéricos en español, con variantes cocinadas) para probar el registro de comidas y el emparejamiento de fotos.

## Tareas
- [ ] Crear `alimentos_desarrollo.csv`
- [ ] Crear `ImportadorAlimentosCsv`, `ResultadoImportacion` y `CargadorDatosDesarrollo` (perfil `local`)
- [ ] Crear `ImportadorAlimentosCsvTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] Al arrancar con el perfil `local` y la tabla vacía se cargan los alimentos de desarrollo
- [ ] Los alimentos de desarrollo tienen `referencia_externa = DEV`

## Clases y ficheros
- `catalogo.dto`: `ResultadoImportacion`
- `catalogo.servicio`: `ImportadorAlimentosCsv`
- `comun.config`: `CargadorDatosDesarrollo`
- pruebas: `catalogo.ImportadorAlimentosCsvTest`
- recursos: `src/main/resources/datos/alimentos_desarrollo.csv`

## Depende de
#13

## Guía
Paso 14 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_14

echo "Creando issue 15/37..."
gh issue create --title "[Backend] Registro manual de comidas, historial y duplicado (migración V4)" --body-file - <<'FIN_ISSUE_15'
## Objetivo
Registrar comidas con alimentos y cantidades en gramos, mililitros o medidas caseras, guardando una instantánea de los nutrientes y avisando de alérgenos e intolerancias, y consultar, editar, duplicar y eliminar el historial.

## Tareas
- [ ] Crear `V4__comidas.sql`, las entidades `RegistroComida` y `AlimentoRegistrado` y sus enums
- [ ] Crear `Nutrientes`, `CalculadoraNutricional`, `RestriccionesUsuario`, `RestriccionesServicio` y `ConversorUnidades`
- [ ] Crear los DTO, `ComidaMapper`, `ComidaServicio` y `ComidaController`
- [ ] Añadir `GET /alimentos/recientes` y `GET /alimentos/frecuentes`
- [ ] Crear `CalculadoraNutricionalTest`, `ConversorUnidadesTest` y `ComidaControllerTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] `1 unidad mediana` de plátano se convierte a gramos y los totales de la comida se calculan
- [ ] Editar un alimento del catálogo no cambia las comidas ya registradas (instantánea)
- [ ] La comida de otro usuario devuelve 404
- [ ] Registrar un alimento con un alérgeno restringido, trazas o alérgenos sin revisar devuelve la comida con un aviso

## Clases y ficheros
- `comun.dto`: `Nutrientes`
- `catalogo.servicio`: `CalculadoraNutricional`
- `perfil.servicio`: `RestriccionesServicio`, `RestriccionesUsuario`
- `comidas.entidad`: `RegistroComida`, `AlimentoRegistrado`, `TipoComida`, `OrigenRegistro`
- `comidas.repositorio`: `RegistroComidaRepositorio`
- `comidas.servicio`: `ConversorUnidades`, `ComidaServicio`
- `comidas.dto`: `RegistroComidaRequest`, `AlimentoRegistradoRequest`, `RegistroComidaResponse`, `AlimentoRegistradoResponse`, `ComidasDiaResponse`, `DuplicarComidaRequest`
- `comidas.mapper`: `ComidaMapper`
- `comidas.web`: `ComidaController`
- pruebas: `catalogo.CalculadoraNutricionalTest`, `comidas.ConversorUnidadesTest`, `comidas.ComidaControllerTest`
- recursos: `db/migration/V4__comidas.sql`

## Depende de
#14

## Guía
Paso 15 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_15

echo "Creando issue 16/37..."
gh issue create --title "[Backend] Entrenamientos planificados y realizados, gasto neto (MET − 1) y objetivo diario guardado (migración V5)" --body-file - <<'FIN_ISSUE_16'
## Objetivo
Registrar y planificar entrenamientos, estimar su gasto neto con valores MET y guardar el objetivo de cada día en `ObjetivoDiario`, para que el historial no cambie al actualizar el perfil.

## Tareas
- [ ] Crear `V5__actividad_y_objetivos.sql` y `R__tipos_actividad.sql`
- [ ] Crear `TipoActividad`, `Entrenamiento`, `Intensidad`, `EstadoEntrenamiento`, `ObjetivoDiario` y sus repositorios
- [ ] Crear `CalculadoraGasto`, `ObjetivoDiarioServicio` y `OmitirEntrenamientosTarea`
- [ ] Crear los DTO, `EntrenamientoMapper`, `EntrenamientoServicio` y `EntrenamientoController`
- [ ] Crear `CalculadoraGastoTest` y `ObjetivoDiarioServicioTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] 60 min de carrera (9,8 MET), 70 kg, intensidad MEDIA → 616 kcal netas
- [ ] Con ese entrenamiento el objetivo del día sube 616 kcal (como hidratos: +154 g) y se guarda en `objetivo_diario`
- [ ] Un entrenamiento planificado no realizado pasa a `OMITIDO` al día siguiente y deja de contar
- [ ] Registrar un entrenamiento sin perfil devuelve 422 `PERFIL_REQUERIDO`

## Clases y ficheros
- `actividad.entidad`: `TipoActividad`, `Entrenamiento`, `Intensidad`, `EstadoEntrenamiento`
- `actividad.repositorio`: `TipoActividadRepositorio`, `EntrenamientoRepositorio`
- `actividad.servicio`: `CalculadoraGasto`, `EntrenamientoServicio`, `OmitirEntrenamientosTarea`
- `perfil.entidad`: `ObjetivoDiario`, `OrigenObjetivo`
- `perfil.repositorio`: `ObjetivoDiarioRepositorio`
- `perfil.servicio`: `ObjetivoDiarioServicio`
- `actividad.dto`: `EntrenamientoRequest`, `EntrenamientoResponse`, `TipoActividadResponse`
- `perfil.dto`: `ObjetivoDiarioResponse`
- `actividad.mapper`: `EntrenamientoMapper`
- `actividad.web`: `EntrenamientoController`
- pruebas: `actividad.CalculadoraGastoTest`, `perfil.ObjetivoDiarioServicioTest`
- recursos: `db/migration/V5__actividad_y_objetivos.sql`, `db/migration/R__tipos_actividad.sql`

## Depende de
#15

## Guía
Paso 16 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_16

echo "Creando issue 17/37..."
gh issue create --title "[Backend] Registro de peso con media móvil y resumen diario del dashboard (migración V6)" --body-file - <<'FIN_ISSUE_17'
## Objetivo
Registrar pesajes, mostrar su evolución con media móvil de 7 días y ofrecer el resumen diario que necesita el dashboard: consumido frente a objetivo, entrenamientos y comidas.

## Tareas
- [ ] Crear `V6__registro_peso.sql`, `RegistroPeso` y su repositorio
- [ ] Crear `PesoServicio` (actualiza el peso del perfil y recalcula objetivos)
- [ ] Crear `ResumenDiarioServicio` y `SeguimientoController`
- [ ] Crear `PesoServicioTest` y `ResumenDiarioTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] Registrar el pesaje más reciente actualiza `pesoActualKg` del perfil y los objetivos automáticos
- [ ] `GET /seguimiento/resumen?fecha=` devuelve consumido, objetivo (con entreno), restante y porcentajes
- [ ] Hito: **núcleo del MVP terminado** (registro manual, perfil, actividad y dashboard)

## Clases y ficheros
- `seguimiento.entidad`: `RegistroPeso`
- `seguimiento.repositorio`: `RegistroPesoRepositorio`
- `seguimiento.servicio`: `PesoServicio`, `ResumenDiarioServicio`
- `seguimiento.dto`: `PesoRequest`, `PesoResponse`, `SeriePesoResponse`, `ResumenDiarioResponse`
- `seguimiento.web`: `SeguimientoController`
- pruebas: `seguimiento.PesoServicioTest`, `seguimiento.ResumenDiarioTest`
- recursos: `db/migration/V6__registro_peso.sql`

## Depende de
#16

## Guía
Paso 17 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_17

echo "Creando issue 18/37..."
gh issue create --title "[Backend] Almacenamiento de fotografías en MinIO/S3 con URLs prefirmadas y procesado de imágenes" --body-file - <<'FIN_ISSUE_18'
## Objetivo
Guardar las fotografías en un bucket privado compatible con S3, sin metadatos EXIF y redimensionadas, y servirlas solo mediante URLs prefirmadas de corta duración.

## Tareas
- [ ] Añadir AWS SDK v2 (S3) y el módulo MinIO de Testcontainers
- [ ] Crear `AlmacenamientoProperties`, `AlmacenamientoConfig`, `InicializadorBucket`, `AlmacenamientoServicio`, `ClavesAlmacenamiento`, `ProcesadorImagenes` e `ImagenProcesada`
- [ ] Añadir MinIO a `IntegracionBase`
- [ ] Crear `ProcesadorImagenesTest` y `AlmacenamientoServicioTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] Al arrancar se crea el bucket `nutrisport-fotos` si no existe
- [ ] Una imagen procesada no contiene el segmento EXIF (`APP1`) y su lado largo es ≤ 1024 px
- [ ] Un fichero que no es JPEG ni PNG devuelve 415

## Clases y ficheros
- `almacenamiento`: `AlmacenamientoProperties`, `AlmacenamientoConfig`, `InicializadorBucket`, `AlmacenamientoServicio`, `ClavesAlmacenamiento`, `ProcesadorImagenes`, `ImagenProcesada`
- pruebas: `almacenamiento.ProcesadorImagenesTest`, `almacenamiento.AlmacenamientoServicioTest`

## Depende de
#17

## Guía
Paso 18 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_18

echo "Creando issue 19/37..."
gh issue create --title "[Backend] Capa de integración de IA: Spring AI, puertos, modo simulado, límites y registro de uso (migración V7)" --body-file - <<'FIN_ISSUE_19'
## Objetivo
Preparar la integración con el proveedor de IA detrás de puertos intercambiables, con un modo simulado para desarrollar y testear sin clave, *timeout* y un reintento, límites diarios por usuario y registro de consumo y coste.

## Tareas
- [ ] Añadir Spring AI (BOM + starter del proveedor elegido)
- [ ] Crear `IaProperties`, `V7__uso_ia.sql`, `UsoIa`, `FuncionIa`, `UsoIaRepositorio`, `UsoIaServicio` y `LimiteIaServicio`
- [ ] Crear el puerto `GeneracionTextoPuerto`, `ResultadoIa`, `IaConfig`, el adaptador real y el simulado
- [ ] Crear `LimiteIaServicioTest`
- [ ] Crear `SchedulingConfig` y activar las tareas programadas de los pasos 10 y 16

## Criterios de aceptación
- [ ] `./mvnw test` pasa con `app.ia.modo=simulado` (sin clave)
- [ ] Con `IA_API_KEY` y `app.ia.modo=real` la aplicación arranca y responde una generación de prueba
- [ ] El análisis número 21 del día devuelve 429 y un usuario sin email verificado recibe 403 `EMAIL_NO_VERIFICADO`

## Clases y ficheros
- `comun.config`: `IaProperties`
- `ia.entidad`: `UsoIa`, `FuncionIa`
- `ia.repositorio`: `UsoIaRepositorio`
- `ia.servicio`: `UsoIaServicio`, `LimiteIaServicio`
- `ia.puerto`: `GeneracionTextoPuerto`, `ResultadoIa`
- `ia.config`: `IaConfig`
- `ia.adaptador`: `SpringAiGeneracionTextoAdaptador`
- `ia.adaptador.simulado`: `GeneracionTextoSimulada`
- `comun.config`: `SchedulingConfig`
- pruebas: `ia.LimiteIaServicioTest`
- recursos: `db/migration/V7__uso_ia.sql`, `src/main/resources/prompts/ (plantillas .st)`

## Depende de
#18

## Guía
Paso 19 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_19

echo "Creando issue 20/37..."
gh issue create --title "[Backend] Reconocimiento de alimentos por foto: subida, análisis asíncrono y emparejamiento con el catálogo (migración V8)" --body-file - <<'FIN_ISSUE_20'
## Objetivo
Subir la foto de un plato, analizarla en segundo plano con el modelo de visión, emparejar cada alimento detectado con el catálogo y calcular la estimación nutricional con datos del catálogo.

## Tareas
- [ ] Crear `V8__analisis_imagen.sql`, `AnalisisImagen`, `TipoAnalisis`, `EstadoAnalisis` y su repositorio
- [ ] Crear el puerto `ReconocimientoAlimentosPuerto`, `AlimentoDetectado`, `ContextoReconocimiento`, el adaptador real (con su plantilla) y el simulado
- [ ] Crear `AsyncConfig`, `EmparejadorCatalogo`, `AnalisisCreadoEvento`, `ProcesadorAnalisis` y `AnalisisImagenServicio`
- [ ] Crear los DTO y `AnalisisController` (`POST /analisis/imagenes`, `GET /analisis/{id}`, `GET /analisis/{id}/imagen`)
- [ ] Crear `AnalisisImagenTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] `POST /analisis/imagenes` responde 202 con `Location` y estado `PENDIENTE`
- [ ] En modo simulado el análisis pasa a `COMPLETADO` con 4 candidatos emparejados y nutrientes calculados desde el catálogo
- [ ] Un análisis que lleva más de 2 minutos sin terminar pasa a `FALLIDO` (tarea programada)

## Clases y ficheros
- `comidas.entidad`: `AnalisisImagen`, `TipoAnalisis`, `EstadoAnalisis`
- `comidas.repositorio`: `AnalisisImagenRepositorio`
- `ia.puerto`: `ReconocimientoAlimentosPuerto`, `AlimentoDetectado`, `ContextoReconocimiento`
- `ia.adaptador`: `SpringAiReconocimientoAdaptador`
- `ia.adaptador.simulado`: `ReconocimientoSimulado`
- `comun.config`: `AsyncConfig`
- `comidas.servicio`: `EmparejadorCatalogo`, `AnalisisCreadoEvento`, `ProcesadorAnalisis`, `AnalisisImagenServicio`, `AnalisisHuerfanosTarea`
- `comidas.dto`: `AnalisisResponse`, `CandidatoAnalisis`, `CoincidenciaCatalogo`, `UrlImagenResponse`
- `comidas.web`: `AnalisisController`
- pruebas: `comidas.AnalisisImagenTest`
- recursos: `db/migration/V8__analisis_imagen.sql`, `src/main/resources/prompts/reconocimiento-alimentos.st`

## Depende de
#19

## Guía
Paso 20 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_20

echo "Creando issue 21/37..."
gh issue create --title "[Backend] Confirmar o descartar el análisis de una foto, métricas de corrección y retención de imágenes" --body-file - <<'FIN_ISSUE_21'
## Objetivo
Convertir un análisis revisado por el usuario en una comida registrada, medir cuánto corrigió el usuario la propuesta de la IA y eliminar las fotos al terminar su periodo de retención.

## Tareas
- [ ] Crear `ConfirmarAnalisisRequest` y `AlimentoConfirmadoRequest`
- [ ] Crear `CalculadoraCorrecciones` y `Correcciones`
- [ ] Añadir `POST /analisis/{id}/confirmar` y `/descartar`
- [ ] Crear `CalculadoraCorreccionesTest` y `ConfirmacionAnalisisTest`
- [ ] Crear `LimpiezaImagenesTarea` y borrar las fotos en `EliminacionCuentaServicio` (TODO del paso 10)

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] Confirmar crea un `RegistroComida` con origen `FOTO` y marca el análisis `CONFIRMADO`
- [ ] Confirmar un análisis que no está `COMPLETADO` devuelve 409
- [ ] Hito: **MVP de registro inteligente terminado**

## Clases y ficheros
- `comidas.dto`: `ConfirmarAnalisisRequest`, `AlimentoConfirmadoRequest`
- `comidas.servicio`: `CalculadoraCorrecciones`, `Correcciones`, `LimpiezaImagenesTarea`
- pruebas: `comidas.CalculadoraCorreccionesTest`, `comidas.ConfirmacionAnalisisTest`

## Depende de
#20

## Guía
Paso 21 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_21

echo "Creando issue 22/37..."
gh issue create --title "[Backend] Administración: usuarios, catálogo de alimentos con revisión de alérgenos, categorías, importación y auditoría (migración V9)" --body-file - <<'FIN_ISSUE_22'
## Objetivo
Que el administrador gestione usuarios (sin ver sus datos de salud) y el catálogo público de alimentos (incluida la revisión de alérgenos), importe alimentos y desactive los que ya están en uso, dejando registro de auditoría de cada acción.

## Tareas
- [ ] Crear `V9__auditoria.sql`, `Auditoria`, su repositorio y `AuditoriaServicio`
- [ ] Crear `AdminUsuarioServicio`, `AdminCatalogoServicio` y sus DTO
- [ ] Crear `AdminUsuarioController`, `AdminAlimentoController` y `AdminCategoriaController`
- [ ] Crear `AdminControllerTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] Un deportista recibe 403 en `/api/v1/admin/**`
- [ ] Bloquear a un usuario revoca sus sesiones y su login falla
- [ ] Marcar los alérgenos de un alimento como revisados queda registrado en la auditoría
- [ ] Eliminar un alimento usado en una comida lo desactiva (no lo borra)

## Clases y ficheros
- `admin.entidad`: `Auditoria`
- `admin.repositorio`: `AuditoriaRepositorio`
- `admin.servicio`: `AuditoriaServicio`, `AdminUsuarioServicio`, `AdminCatalogoServicio`
- `admin.dto`: `UsuarioAdminResponse`, `ActualizarUsuarioAdminRequest`, `CategoriaRequest`, `RevisionAlergenosRequest`
- `admin.web`: `AdminUsuarioController`, `AdminAlimentoController`, `AdminCategoriaController`
- pruebas: `admin.AdminControllerTest`
- recursos: `db/migration/V9__auditoria.sql`

## Depende de
#21

## Guía
Paso 22 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_22

echo "Creando issue 23/37..."
gh issue create --title "[Backend] Dockerfile multi-etapa y servicio backend en Docker Compose" --body-file - <<'FIN_ISSUE_23'
## Objetivo
Empaquetar el backend en una imagen Docker ligera, sin root y con healthcheck, y poder levantar la pila completa con Docker Compose.

## Tareas
- [ ] Crear `Dockerfile` multi-etapa y `.dockerignore`
- [ ] Añadir el servicio `backend` a `docker-compose.yml` con el perfil `app`
- [ ] Comprobar el arranque y el healthcheck

## Criterios de aceptación
- [ ] `docker compose --profile app up -d --build` levanta los cuatro servicios
- [ ] `curl http://localhost:8080/actuator/health` devuelve `UP`
- [ ] El contenedor del backend no se ejecuta como root
- [ ] Las URLs prefirmadas de las fotos funcionan desde el navegador (`endpoint-publico`)

## Clases y ficheros
- recursos: `Dockerfile`, `.dockerignore`

## Depende de
#22

## Guía
Paso 23 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_23

echo "Creando issue 24/37..."
gh issue create --title "[Backend] Revisión de seguridad (OWASP Top 10 y OWASP Top 10 para LLM) y correcciones" --body-file - <<'FIN_ISSUE_24'
## Objetivo
Revisar el backend frente a OWASP Top 10 y OWASP Top 10 para aplicaciones con LLM, corregir lo encontrado y dejar tests que impidan que un usuario acceda a datos de otro.

## Tareas
- [ ] Ejecutar la revisión con el prompt de la guía y corregir los hallazgos
- [ ] Crear `AutorizacionRecursosTest` (acceso cruzado entre usuarios en todos los módulos)
- [ ] Crear `CabecerasSeguridadTest`
- [ ] Configurar producción: `server.forward-headers-strategy`, cookie `Secure` y Actuator mínimo

## Criterios de aceptación
- [ ] `./mvnw test` pasa, incluidos los tests nuevos
- [ ] Ningún recurso personal es accesible por otro usuario (404 en todos los casos)
- [ ] `git grep` no encuentra secretos en el repositorio
- [ ] Los hallazgos y su corrección quedan descritos en la PR

## Clases y ficheros
- pruebas: `seguridad.AutorizacionRecursosTest`, `seguridad.CabecerasSeguridadTest`

## Depende de
#23

## Guía
Paso 24 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_24

echo "Creando issue 25/37..."
gh issue create --title "[Backend] Prueba de integración del flujo completo del MVP" --body-file - <<'FIN_ISSUE_25'
## Objetivo
Tener un test que recorra el uso real de la aplicación de principio a fin y sirva de guion para la demostración.

## Tareas
- [ ] Crear `FlujoCompletoTest` con el recorrido completo
- [ ] Corregir lo que falle

## Criterios de aceptación
- [ ] `./mvnw test` pasa, incluido `FlujoCompletoTest`
- [ ] El test recorre: registro → verificación → perfil y objetivos → comida manual → foto (simulada) → confirmación → entrenamiento → resumen diario → revocar y dar el consentimiento → eliminar la cuenta

## Clases y ficheros
- pruebas: `FlujoCompletoTest`

## Depende de
#24

## Guía
Paso 25 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_25

echo "Creando issue 26/37..."
gh issue create --title "[Backend] Documentar los endpoints con OpenAPI y escribir el README del proyecto" --body-file - <<'FIN_ISSUE_26'
## Objetivo
Que cualquier persona pueda entender, arrancar y probar el backend: Swagger completo y un README con instrucciones, decisiones y atribuciones.

## Tareas
- [ ] Completar `@Tag`, `@Operation`, `@ApiResponse` y ejemplos en todos los controladores
- [ ] Ocultar Swagger UI fuera de los perfiles `local` y `dev`
- [ ] Escribir el `README.md`

## Criterios de aceptación
- [ ] Swagger muestra todos los grupos (Auth, Cuenta, Perfil, Catálogo, Comidas, Análisis, Actividad, Seguimiento, Admin y, en la Ampliación, Productos, Recetas, Menús, Compra y Asistente) con descripciones en castellano
- [ ] Los errores aparecen documentados como `application/problem+json`
- [ ] El README permite arrancar el proyecto desde cero siguiendo sus pasos

## Clases y ficheros
- recursos: `README.md (raíz)`

## Depende de
#25

## Guía
Paso 26 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_26

echo "Creando issue 27/37..."
gh issue create --title "[Backend] A1 · Integración con Open Food Facts: búsqueda de productos, código de barras, normalización y caché (migración V10)" --body-file - <<'FIN_ISSUE_27'
## Objetivo
Consultar productos comerciales reales desde el backend (nunca desde Angular), normalizarlos a `Alimento` con su procedencia en `AlimentoFuente`, respetar los límites de Open Food Facts con caché y Bucket4j, y seguir funcionando con los datos locales si el proveedor no responde.

## Tareas
- [ ] Crear `V10__fuentes_externas.sql` (`alimento_fuente` y campos de producto en `alimento`), `AlimentoFuente`, `ProveedorExterno` y su repositorio
- [ ] Crear `LimitadorProveedor`, `EstadoProveedor`, `CacheConfig` e `IntegracionProperties`
- [ ] Crear `OpenFoodFactsServicio`, `ProductoExternoDto`, `NormalizacionAlimentosServicio` y `TablaAlergenosOff`
- [ ] Crear `ProductoServicio`, los DTO y `ProductoController` (`GET /productos?q=`, `GET /productos/{codigoBarras}`); aceptar `codigoBarras` en `POST /comidas`
- [ ] Crear `OpenFoodFactsServicioTest`, `NormalizacionAlimentosServicioTest` y `ProductoControllerTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa sin llamar a Open Food Facts (respuestas grabadas)
- [ ] `GET /productos/{codigo}` de un producto existente lo devuelve con nutrientes por 100 g, alérgenos, imagen y atribución, y lo guarda con `AlimentoFuente`
- [ ] Un código inexistente devuelve 404; con Open Food Facts caído, un código ya guardado funciona y uno nuevo devuelve 503 `PROVEEDOR_NO_DISPONIBLE`
- [ ] Un producto sin energía o sin algún macronutriente tiene `nutricionCompleta = false` y no se puede registrar (422 `NUTRICION_INCOMPLETA`)
- [ ] Nunca se superan 8 búsquedas/min ni 12 productos/min hacia Open Food Facts

## Clases y ficheros
- `integracion.comun`: `IntegracionProperties`, `LimitadorProveedor`, `EstadoProveedor`, `ProveedorExterno`
- `comun.config`: `CacheConfig`
- `catalogo.entidad`: `AlimentoFuente`
- `catalogo.repositorio`: `AlimentoFuenteRepositorio`
- `integracion.openfoodfacts`: `OpenFoodFactsServicio`, `ProductoExternoDto`, `TablaAlergenosOff`
- `catalogo.servicio`: `NormalizacionAlimentosServicio`, `ProductoServicio`
- `catalogo.dto`: `ProductoResponse`, `ProductoResumenResponse`, `FuenteResponse`
- `catalogo.web`: `ProductoController`
- pruebas: `integracion.OpenFoodFactsServicioTest`, `catalogo.NormalizacionAlimentosServicioTest`, `catalogo.ProductoControllerTest`
- recursos: `db/migration/V10__fuentes_externas.sql`, `src/test/resources/integracion/off-*.json (respuestas grabadas)`

## Depende de
#26

## Guía
Paso 27 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_27

echo "Creando issue 28/37..."
gh issue create --title "[Backend] A2 · Recetas con TheMealDB: modelo, catálogo, importación, favoritas y relacionadas (migración V11)" --body-file - <<'FIN_ISSUE_28'
## Objetivo
Tener recetas propias e importadas de TheMealDB: buscar, filtrar por ingrediente, categoría y cocina, importar el detalle con sus ingredientes y medidas originales, guardar favoritas y ver relacionadas. El cálculo nutricional llega en A3.

## Tareas
- [ ] Crear `V11__recetas.sql` (receta, receta_ingrediente, receta_favorita, `categoria.codigo_externo`) y sus entidades y repositorios
- [ ] Crear `TheMealDbServicio`, `RecetaExternaDto` e `IngredienteExternoDto`
- [ ] Crear `RecetaServicio`, `ImportadorRecetasJson`, los DTO y `RecetaController`
- [ ] Crear `TheMealDbServicioTest` y `RecetaControllerTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa sin llamar a TheMealDB (respuestas grabadas)
- [ ] `GET /recetas/externas/themealdb/52850` importa *Chicken Couscous* con sus 10 ingredientes y medidas originales y devuelve un `id` propio; la segunda vez no llama a TheMealDB
- [ ] `GET /recetas?ingrediente=chicken_breast&origen=themealdb` lista recetas con `referenciaExterna` y estado "cálculo pendiente"
- [ ] Favoritas: `PUT` y `DELETE /recetas/{id}/favorita` y `GET /recetas/favoritas`

## Clases y ficheros
- `catalogo.entidad`: `Receta`, `RecetaIngrediente`, `RecetaFavorita`, `PasoReceta`, `Dificultad`, `OrigenReceta`, `EstadoNutricional`, `EstadoConversion`
- `catalogo.repositorio`: `RecetaRepositorio`, `RecetaFavoritaRepositorio`
- `integracion.themealdb`: `TheMealDbServicio`, `RecetaExternaDto`, `IngredienteExternoDto`, `TablaCategoriasMealDb`
- `catalogo.servicio`: `RecetaServicio`, `ImportadorRecetasJson`
- `catalogo.dto`: `RecetaResponse`, `RecetaResumenResponse`, `IngredienteResponse`, `CategoriaRecetaResponse`
- `catalogo.mapper`: `RecetaMapper`
- `catalogo.web`: `RecetaController`
- pruebas: `integracion.TheMealDbServicioTest`, `catalogo.RecetaControllerTest`
- recursos: `db/migration/V11__recetas.sql`, `src/main/resources/datos/recetas_desarrollo.json`, `src/test/resources/integracion/mealdb-*.json`

## Depende de
#27

## Guía
Paso 28 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_28

echo "Creando issue 29/37..."
gh issue create --title "[Backend] A3 · Cálculo nutricional de recetas: USDA FoodData Central, emparejamiento de ingredientes y estado del cálculo (migración V12)" --body-file - <<'FIN_ISSUE_29'
## Objetivo
Calcular los nutrientes por ración de las recetas externas a partir de sus ingredientes, con datos de USDA FoodData Central y del catálogo, indicando siempre si el cálculo es completo, aproximado o incompleto; y permitir que el administrador revise emparejamientos y publique recetas.

## Tareas
- [ ] Crear `V12__mapeo_ingredientes.sql`, `MapeoIngrediente` y su repositorio
- [ ] Crear `FoodDataCentralServicio`, `AlimentoUsdaDto` y la normalización a `Alimento` (fuente USDA)
- [ ] Crear `AnalizadorMedidas`, `MapeoIngredientesServicio` y `CalculoNutricionalServicio`
- [ ] Añadir `GET /recetas/{id}/nutricion` y la administración de mapeos y de recetas (`AdminRecetaController`)
- [ ] Crear `AnalizadorMedidasTest`, `CalculoNutricionalServicioTest` y `FoodDataCentralServicioTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa sin llamar a USDA (respuestas grabadas)
- [ ] *Chicken Couscous* con la harissa sin emparejar queda `APROXIMADO` con completitud ≥ 90 % y raciones supuestas
- [ ] "1 tbsp" se convierte con la porción de USDA; "pinch" y "Handful" de hierbas son despreciables; "200g" es exacta
- [ ] Una receta con < 90 % de la masa resuelta queda `INCOMPLETO` y no devuelve totales por ración
- [ ] Al corregir un mapeo en `/admin/mapeos-ingredientes`, se recalculan las recetas afectadas

## Clases y ficheros
- `catalogo.entidad`: `MapeoIngrediente`, `MetodoMapeo`
- `catalogo.repositorio`: `MapeoIngredienteRepositorio`
- `integracion.usda`: `FoodDataCentralServicio`, `AlimentoUsdaDto`, `PorcionUsdaDto`
- `catalogo.servicio`: `AnalizadorMedidas`, `MedidaInterpretada`, `MapeoIngredientesServicio`, `CalculoNutricionalServicio`
- `catalogo.dto`: `NutricionRecetaResponse`, `IngredienteCalculoResponse`, `MapeoIngredienteResponse`, `ActualizarMapeoRequest`, `PublicarRecetaRequest`
- `admin.web`: `AdminRecetaController`, `AdminMapeoController`
- pruebas: `catalogo.AnalizadorMedidasTest`, `catalogo.CalculoNutricionalServicioTest`, `integracion.FoodDataCentralServicioTest`
- recursos: `db/migration/V12__mapeo_ingredientes.sql`, `src/test/resources/integracion/usda-*.json`

## Depende de
#28

## Guía
Paso 29 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_29

echo "Creando issue 30/37..."
gh issue create --title "[Backend] Menús semanales: modelo, consulta con totales por día y gestión básica (migración V13)" --body-file - <<'FIN_ISSUE_30'
## Objetivo
Guardar menús diarios y semanales con sus comidas planificadas y consultarlos con los totales de cada día frente al objetivo.

## Tareas
- [ ] Crear `V13__menus.sql`, `MenuSemanal`, `ComidaPlanificada`, `TipoMenu`, `EstadoMenu` y sus repositorios
- [ ] Crear los DTO, `MenuMapper`, `MenuServicio` y `MenuController` (listar, detalle, PATCH y DELETE)
- [ ] Crear `MenuControllerTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] `GET /menus/{id}` devuelve cada día con sus comidas, el total planificado y el objetivo del día
- [ ] `PATCH /menus/{id}` marca y desmarca favorito

## Clases y ficheros
- `menus.entidad`: `MenuSemanal`, `ComidaPlanificada`, `TipoMenu`, `EstadoMenu`
- `menus.repositorio`: `MenuSemanalRepositorio`, `ComidaPlanificadaRepositorio`
- `menus.dto`: `MenuResponse`, `DiaMenuResponse`, `ComidaPlanificadaResponse`, `MenuResumenResponse`, `ActualizarMenuRequest`
- `menus.mapper`: `MenuMapper`
- `menus.servicio`: `MenuServicio`
- `menus.web`: `MenuController`
- pruebas: `menus.MenuControllerTest`
- recursos: `db/migration/V13__menus.sql`

## Depende de
#29

## Guía
Paso 30 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_30

echo "Creando issue 31/37..."
gh issue create --title "[Backend] Motor de recomendación y generación de menús diarios y semanales" --body-file - <<'FIN_ISSUE_31'
## Objetivo
Generar menús que respeten siempre las restricciones estrictas y se ajusten a los objetivos de cada día, con un motor de reglas determinista que solo usa recetas publicadas y calculadas; la IA, opcional, personaliza y propone ajustes que el backend valida.

## Tareas
- [ ] Crear `RepartoFranjas`, `ContextoGeneracion`, `SeleccionFranja`, `PuntuadorRecetas` y `MotorRecomendacion`
- [ ] Crear `GeneradorMenuServicio`, `GenerarMenuRequest` y `POST /menus/generar`
- [ ] Crear la plantilla `explicacion-menu.st`
- [ ] Crear `SugerenciaIaServicio` (opcional): ajustes y sustituciones propuestos por la IA y validados por el backend
- [ ] Crear `MotorRecomendacionTest` y `GenerarMenuTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] En 7 días generados con datos sintéticos, el 100 % de las recetas cumple las restricciones estrictas
- [ ] Cada día queda dentro de ±10 % del objetivo energético (o el día lleva un aviso)
- [ ] Si falla la IA, el menú se genera igualmente con una explicación de plantilla
- [ ] Una sugerencia de la IA con un alimento que no existe en el catálogo se descarta

## Clases y ficheros
- `menus.recomendacion`: `RepartoFranjas`, `ContextoGeneracion`, `SeleccionFranja`, `PuntuadorRecetas`, `MotorRecomendacion`
- `menus.servicio`: `GeneradorMenuServicio`
- `menus.dto`: `GenerarMenuRequest`
- `menus.servicio`: `SugerenciaIaServicio`
- `menus.dto`: `SugerenciaIaResponse`
- pruebas: `menus.MotorRecomendacionTest`, `menus.GenerarMenuTest`
- recursos: `src/main/resources/prompts/explicacion-menu.st`, `src/main/resources/prompts/sugerencia-menu.st`

## Depende de
#30

## Guía
Paso 31 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_31

echo "Creando issue 32/37..."
gh issue create --title "[Backend] Edición de menús: sustituir, mover, bloquear, regenerar, duplicar y registrar comidas planificadas" --body-file - <<'FIN_ISSUE_32'
## Objetivo
Ajustar un menú generado: alternativas equivalentes, cambios manuales, bloqueo, regeneración de lo no bloqueado, reutilización en otra semana y paso de comida planificada a comida registrada.

## Tareas
- [ ] Crear `EdicionMenuServicio` y los DTO `ActualizarComidaPlanificadaRequest`, `DuplicarMenuRequest` y `AlternativaResponse`
- [ ] Añadir los endpoints de añadir y quitar comidas, recetas disponibles, regenerar, duplicar, editar comida, alternativas, registrar y `POST /comidas/desde-receta`
- [ ] Completar `comidasPlanificadasPendientes` en el resumen diario
- [ ] Crear `EdicionMenuTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] Las alternativas son 3 recetas compatibles con la energía dentro de ±15 % de la original
- [ ] Regenerar no cambia las comidas bloqueadas ni las consumidas
- [ ] Registrar una comida planificada crea un `RegistroComida` con origen `PLAN` y la marca consumida
- [ ] Añadir una receta con cálculo incompleto devuelve 422 `NUTRICION_INCOMPLETA`

## Clases y ficheros
- `menus.servicio`: `EdicionMenuServicio`
- `menus.dto`: `ActualizarComidaPlanificadaRequest`, `DuplicarMenuRequest`, `AlternativaResponse`, `AnadirComidaRequest`, `RegistrarRecetaRequest`
- pruebas: `menus.EdicionMenuTest`

## Depende de
#31

## Guía
Paso 32 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_32

echo "Creando issue 33/37..."
gh issue create --title "[Backend] Lista de la compra generada desde el menú, agrupada por sección y con cantidades sumadas (migración V14)" --body-file - <<'FIN_ISSUE_33'
## Objetivo
Convertir un menú (o un rango de sus días) en una lista de la compra práctica: ingredientes agrupados por sección del supermercado, cantidades sumadas y normalizadas, productos comprados y edición manual.

## Tareas
- [ ] Crear `V14__lista_compra.sql`, `ListaCompra`, `ProductoCompra`, `EstadoLista` y sus repositorios
- [ ] Crear `GeneradorListaCompra` y `ListaCompraServicio`
- [ ] Crear los DTO y `ListaCompraController` (incluido "añadir desde receta")
- [ ] Crear `GeneradorListaCompraTest` y `ListaCompraControllerTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] 200 g de arroz el lunes y 150 g el miércoles se convierten en `Arroz · 350 g` en la sección `Despensa`
- [ ] 1200 g se muestran como 1,2 kg
- [ ] Marcar productos comprados actualiza el progreso (`comprados / total`)

## Clases y ficheros
- `compra.entidad`: `ListaCompra`, `ProductoCompra`, `EstadoLista`
- `compra.repositorio`: `ListaCompraRepositorio`, `ProductoCompraRepositorio`
- `compra.servicio`: `GeneradorListaCompra`, `ListaCompraServicio`
- `compra.dto`: `GenerarListaRequest`, `ListaCompraResponse`, `SeccionCompraResponse`, `ProductoCompraResponse`, `ListaCompraResumenResponse`, `ProductoCompraRequest`, `ActualizarProductoRequest`, `ActualizarListaRequest`
- `compra.web`: `ListaCompraController`
- pruebas: `compra.GeneradorListaCompraTest`, `compra.ListaCompraControllerTest`
- recursos: `db/migration/V14__lista_compra.sql`

## Depende de
#32

## Guía
Paso 33 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_33

echo "Creando issue 34/37..."
gh issue create --title "[Backend] Estadísticas, comparativas y relación entre alimentación y actividad" --body-file - <<'FIN_ISSUE_34'
## Objetivo
Ofrecer las series y agregados de la pantalla de estadísticas: energía y macros frente a objetivo, adherencia, reparto, alimentos más consumidos, comparativa con el periodo anterior y días de entreno frente a descanso.

## Tareas
- [ ] Añadir consultas agregadas en los repositorios de comidas y entrenamientos
- [ ] Crear `EstadisticasServicio`, `ComparativaServicio` y `RelacionNutricionActividadServicio`
- [ ] Crear los DTO y `EstadisticasController`; añadir `GET /entrenamientos/relacion-nutricion`
- [ ] Crear `EstadisticasServicioTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] `GET /seguimiento/estadisticas?desde=&hasta=&agrupacion=dia` devuelve un punto por día con consumido y objetivo
- [ ] Un día cuenta como «en objetivo» si la energía está dentro de ±10 %
- [ ] La comparativa semanal devuelve las variaciones porcentuales respecto a la semana anterior

## Clases y ficheros
- `seguimiento.entidad`: `Agrupacion`
- `seguimiento.servicio`: `EstadisticasServicio`, `ComparativaServicio`, `RelacionNutricionActividadServicio`
- `seguimiento.dto`: `EstadisticasResponse`, `PuntoSerieResponse`, `AlimentoFrecuenteResponse`, `ComparativaResponse`, `RelacionNutricionResponse`
- `seguimiento.web`: `EstadisticasController`
- pruebas: `seguimiento.EstadisticasServicioTest`

## Depende de
#33

## Guía
Paso 34 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_34

echo "Creando issue 35/37..."
gh issue create --title "[Backend] Detección de patrones por reglas y redacción de insights con tarea programada (migración V15)" --body-file - <<'FIN_ISSUE_35'
## Objetivo
Detectar cada noche patrones en los datos de cada usuario con reglas estadísticas y presentarlos como insights; la IA solo redacta el consejo, nunca calcula las cifras.

## Tareas
- [ ] Crear `V15__insights.sql`, `Insight`, `TipoInsight`, `SeveridadInsight` y su repositorio
- [ ] Crear `DatosPeriodo`, `Hallazgo`, `DetectorPatrones` y `RedactorInsights` (con plantilla)
- [ ] Crear `InsightServicio`, `InsightsTarea` y `GET /seguimiento/insights`; añadir `insightsActivos` a la cuenta
- [ ] Crear `DetectorPatronesTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] Con proteína < 80 % del objetivo en 4 de 7 días se genera el insight `PROTEINA_BAJA`
- [ ] Las cifras del texto del insight salen de los datos calculados, no del LLM
- [ ] Un usuario con `insightsActivos = false` no recibe insights

## Clases y ficheros
- `seguimiento.entidad`: `Insight`, `TipoInsight`, `SeveridadInsight`
- `seguimiento.repositorio`: `InsightRepositorio`
- `seguimiento.servicio`: `DatosPeriodo`, `Hallazgo`, `DetectorPatrones`, `RedactorInsights`, `InsightServicio`, `InsightsTarea`
- `seguimiento.dto`: `InsightResponse`
- pruebas: `seguimiento.DetectorPatronesTest`
- recursos: `db/migration/V15__insights.sql`, `src/main/resources/prompts/consejo-insight.st`

## Depende de
#34

## Guía
Paso 35 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_35

echo "Creando issue 36/37..."
gh issue create --title "[Backend] Asistente IA: conversaciones, herramientas de consulta de datos y respuesta completa (migración V16)" --body-file - <<'FIN_ISSUE_36'
## Objetivo
Permitir conversar con un asistente que responde con los datos reales del usuario mediante herramientas de solo lectura (tool calling), con el identificador del usuario tomado siempre del contexto de seguridad.

## Tareas
- [ ] Crear `V16__asistente.sql`, `ConversacionIa`, `MensajeIa`, `RolMensaje` y sus repositorios
- [ ] Crear `HerramientasAsistente` (resumen diario, comidas, menú, entrenamientos y tendencias)
- [ ] Crear el puerto `ChatAsistentePuerto`, `ContextoChat`, `RespuestaChat`, el adaptador real (con prompt de sistema) y el simulado
- [ ] Crear `AsistenteServicio`, los DTO y `AsistenteController`
- [ ] Crear `HerramientasAsistenteTest` y `AsistenteControllerTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] «¿Cuánta proteína tomé ayer?» devuelve la misma cifra que el resumen diario (HU-16)
- [ ] Las herramientas nunca devuelven datos de otro usuario, aunque el texto lo pida
- [ ] Cada respuesta guarda qué herramientas se usaron y los tokens consumidos

## Clases y ficheros
- `asistente.entidad`: `ConversacionIa`, `MensajeIa`, `RolMensaje`
- `asistente.repositorio`: `ConversacionIaRepositorio`, `MensajeIaRepositorio`
- `asistente.herramientas`: `HerramientasAsistente`
- `ia.puerto`: `ChatAsistentePuerto`, `ContextoChat`, `RespuestaChat`
- `ia.adaptador`: `SpringAiChatAsistenteAdaptador`
- `ia.adaptador.simulado`: `ChatAsistenteSimulado`
- `asistente.servicio`: `AsistenteServicio`
- `asistente.dto`: `ConversacionResponse`, `ConversacionResumenResponse`, `MensajeResponse`, `EnviarMensajeRequest`, `ActualizarConversacionRequest`, `ValoracionRequest`
- `asistente.web`: `AsistenteController`
- `asistente.servicio`: `FiltroTemasSensibles`, `RespuestaSegura`
- pruebas: `asistente.HerramientasAsistenteTest`, `asistente.AsistenteControllerTest`, `asistente.FiltroTemasSensiblesTest`
- recursos: `db/migration/V16__asistente.sql`, `src/main/resources/prompts/asistente-sistema.st`, `src/main/resources/prompts/titulo-conversacion.st`

## Depende de
#35

## Guía
Paso 36 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_36

echo "Creando issue 37/37..."
gh issue create --title "[Backend] Exportación de datos de la cuenta (portabilidad, RGPD)" --body-file - <<'FIN_ISSUE_37'
## Objetivo
Cumplir el derecho de acceso y portabilidad: descargar todos los datos del usuario en JSON.

## Tareas
- [ ] Crear `ExportacionServicio` y `ExportacionDatos`
- [ ] Añadir `GET /cuenta/exportacion` (exige email verificado)
- [ ] Crear `ExportacionTest`

## Criterios de aceptación
- [ ] `./mvnw test` pasa
- [ ] La exportación incluye cuenta, perfil, preferencias, objetivos diarios, comidas, entrenamientos, pesos y, si existen, menús, listas, recetas favoritas y conversaciones; no incluye `password_hash`
- [ ] Sin email verificado devuelve 403 `EMAIL_NO_VERIFICADO`

## Clases y ficheros
- `auth.servicio`: `ExportacionServicio`
- `auth.dto`: `ExportacionDatos`
- pruebas: `auth.ExportacionTest`

## Depende de
#36

## Guía
Paso 37 de `docs/Guia_Github_Backend_NutriSport.md`.
FIN_ISSUE_37

echo "Hecho."
