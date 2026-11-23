Estoy desarrollando el backend de NutriSport AI: una API REST para que deportistas registren y analicen 
su alimentación (perfil y objetivos, registro de comidas manual y por foto con IA, entrenamientos y, en 
la Ampliación, productos de Open Food Facts, recetas de TheMealDB con cálculo nutricional USDA, menús, 
lista de la compra, estadísticas y un asistente con IA). La especificación completa está en 
docs/PROJECT_PROPOSAL.md.
Stack: Java 21, Spring Boot 3.x, Maven, Spring Web, Spring Data JPA (Hibernate), PostgreSQL 16, Flyway, 
Spring Security con JWT (jjwt 0.12.x), Jakarta Validation, MapStruct, springdoc-openapi, Spring AI, 
Bucket4j, Caffeine, AWS SDK v2 (S3/MinIO) y Testcontainers.
Proyecto: aplicación Spring Boot (Maven) en la raíz del repositorio (pom.xml, mvnw y src/ en la raíz; la 
documentación en docs/). Paquete raíz com.nutrisport, organizado POR MÓDULOS: comun, auth, perfil, 
catalogo, comidas, actividad, seguimiento, almacenamiento, ia, integracion, menus, compra, asistente y 
admin. Dentro de cada módulo, los subpaquetes entidad, repositorio, servicio, dto, mapper y web 
(controladores) según haga falta.
Reglas que debes cumplir SIEMPRE:
1. Nombres de clases, métodos, variables, comentarios y mensajes en castellano.
2. Sin Lombok: getters y setters explícitos en las entidades. Los DTO son records de Java. Los mappers 
son interfaces MapStruct (componentModel spring).
3. Inyección de dependencias por constructor (nunca @Autowired en campos).
4. Los controladores solo reciben la petición, llaman a un servicio y devuelven DTO. Nunca devuelven 
entidades, password_hash ni tokens almacenados.
5. La lógica de negocio va en los servicios, con @Transactional (readOnly = true en las consultas).
6. El esquema lo crean SOLO las migraciones Flyway de src/main/resources/db/migration 
(V{n}__descripcion.sql); spring.jpa.hibernate.ddl-auto=validate. Las entidades deben coincidir 
exactamente con las migraciones. Nunca modifiques una migración ya fusionada en main: crea una nueva.
7. Las entidades extienden comun.entidad.EntidadBase (id UUID, creadoEn, actualizadoEn), salvo que el 
paso diga otra cosa. Los enums se guardan como texto (@Enumerated(STRING)) con las constantes en 
MAYÚSCULAS idénticas a los CHECK de la migración.
8. El JSON usa camelCase; las fechas, ISO-8601; los valores nutricionales y los pesos son BigDecimal.
9. Todas las rutas empiezan por /api/v1 y siguen docs/PROJECT_PROPOSAL.md §7. Los errores son Problem 
Details (RFC 9457) con las propiedades "codigo" y "errors" (ver 
comun.excepcion.ManejadorGlobalExcepciones).
10. Cada consulta de un recurso personal se filtra por el usuario autenticado 
(auth.seguridad.SeguridadUtils). Un recurso de otro usuario devuelve 404, nunca 403.
11. Inteligencia artificial: solo a través de los puertos de com.nutrisport.ia.puerto, con un adaptador 
real (Spring AI) y otro simulado. La IA nunca calcula valores nutricionales: se calculan con el catálogo 
y los datos de las APIs. Los datos de APIs externas llevan siempre su fuente y atribución. A la IA no se 
le envían nombre, email ni ids del usuario.
12. Secretos solo por variables de entorno (.env, que no se sube). Nunca escribas contraseñas ni claves 
en el código ni en los .properties.
13. Antes de generar nada, lee docs/PROJECT_PROPOSAL.md (la sección que indique el prompt) y el código 
existente que vayas a usar. Si algo no está claro, pregúntame en lugar de inventarlo. No inventes 
versiones de dependencias.
Cuando termines, dime qué archivos has creado o modificado.