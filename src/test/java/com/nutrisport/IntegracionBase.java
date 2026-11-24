package com.nutrisport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de los tests de integración: arranca el contexto completo de Spring con el perfil "test"
 * contra un PostgreSQL 16 real en Docker.
 * <p>
 * El contenedor sigue el patrón singleton: se arranca una sola vez en el bloque estático y lo
 * comparten todas las clases de test que extienden esta base. No se para de forma explícita;
 * Testcontainers (Ryuk) lo elimina al terminar la JVM. Al tener todas las clases la misma
 * configuración, Spring reutiliza también el contexto en caché.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegracionBase {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
