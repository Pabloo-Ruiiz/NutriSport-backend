package com.nutrisport.comun.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cachés en memoria con Caffeine (docs/API_INTEGRATIONS.md §6.5) para no repetir llamadas a las APIs
 * externas, cuyos límites comparten todos los usuarios. Cada caché tiene su duración y su tamaño máximo.
 * <p>
 * Solo existen las cachés declaradas aquí: un {@code @Cacheable} con otro nombre falla en su primera llamada
 * ("Cannot find cache") en lugar de crear en silencio una caché sin caducidad ni tamaño máximo. Los productos
 * y recetas ya importados no van en caché: se guardan en PostgreSQL.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Resultados de búsqueda en Open Food Facts por texto normalizado. */
    public static final String OFF_BUSQUEDA = "off-busqueda";
    /** Listas y filtros de TheMealDB. */
    public static final String MEALDB_LISTAS = "mealdb-listas";
    /** Categorías de TheMealDB (cambian muy poco). */
    public static final String MEALDB_CATEGORIAS = "mealdb-categorias";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager gestor = new CaffeineCacheManager();
        // Sin creación dinámica: solo valen las cachés registradas a continuación
        gestor.setCacheNames(List.of());
        gestor.registerCustomCache(OFF_BUSQUEDA, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .maximumSize(500)
                .build());
        gestor.registerCustomCache(MEALDB_LISTAS, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(24))
                .maximumSize(500)
                .build());
        gestor.registerCustomCache(MEALDB_CATEGORIAS, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofDays(7))
                .maximumSize(50)
                .build());
        return gestor;
    }
}
