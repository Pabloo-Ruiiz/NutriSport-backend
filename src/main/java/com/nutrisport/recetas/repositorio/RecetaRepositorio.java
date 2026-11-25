package com.nutrisport.recetas.repositorio;

import com.nutrisport.recetas.entidad.OrigenReceta;
import com.nutrisport.recetas.entidad.Receta;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Recetas propias e importadas (§6.1 y §7.3). */
public interface RecetaRepositorio extends JpaRepository<Receta, UUID> {

    /** Una receta importada por su idMeal (para no importarla dos veces). */
    Optional<Receta> findByOrigenAndReferenciaExterna(OrigenReceta origen, String referenciaExterna);

    /** Detalle con sus ingredientes y los alimentos emparejados. */
    @EntityGraph(attributePaths = {"ingredientes", "ingredientes.alimento", "categoria"})
    Optional<Receta> findWithIngredientesById(UUID id);

    /**
     * Recetas visibles para el usuario (públicas y privadas propias), en orden alfabético. Todos los filtros son
     * opcionales: un texto vacío o un valor nulo no filtran.
     *
     * @param q           texto normalizado (minúsculas y sin tildes, como nombre_normalizado), contenido en el nombre
     * @param ingrediente texto en minúsculas contenido en el nombre original de algún ingrediente ("chicken")
     * @param alimentoId  alimento del catálogo emparejado con algún ingrediente
     */
    @Query(value = """
            select r from Receta r left join r.propietario p
             where (r.visibilidad = com.nutrisport.catalogo.entidad.Visibilidad.PUBLICO or p.id = :usuarioId)
               and (:q = '' or r.nombreNormalizado like concat('%', :q, '%'))
               and (:categoriaId is null or r.categoria.id = :categoriaId)
               and (:origen is null or r.origen = :origen)
               and (:ingrediente = '' or exists (select 1 from RecetaIngrediente i
                     where i.receta = r and lower(i.nombreOriginal) like concat('%', :ingrediente, '%')))
               and (:alimentoId is null or exists (select 1 from RecetaIngrediente i
                     where i.receta = r and i.alimento.id = :alimentoId))
            """,
            countQuery = """
            select count(r) from Receta r left join r.propietario p
             where (r.visibilidad = com.nutrisport.catalogo.entidad.Visibilidad.PUBLICO or p.id = :usuarioId)
               and (:q = '' or r.nombreNormalizado like concat('%', :q, '%'))
               and (:categoriaId is null or r.categoria.id = :categoriaId)
               and (:origen is null or r.origen = :origen)
               and (:ingrediente = '' or exists (select 1 from RecetaIngrediente i
                     where i.receta = r and lower(i.nombreOriginal) like concat('%', :ingrediente, '%')))
               and (:alimentoId is null or exists (select 1 from RecetaIngrediente i
                     where i.receta = r and i.alimento.id = :alimentoId))
            """)
    Page<Receta> buscar(@Param("q") String q, @Param("categoriaId") UUID categoriaId,
            @Param("origen") OrigenReceta origen, @Param("ingrediente") String ingrediente,
            @Param("alimentoId") UUID alimentoId, @Param("usuarioId") UUID usuarioId, Pageable pageable);

    /**
     * Catálogo de recetas para el usuario (§7.3): públicas publicadas y propias, en orden alfabético. Un texto
     * vacío o un valor nulo no filtran. Los filtros de kcal y proteína solo dejan pasar recetas con cálculo
     * COMPLETO o APROXIMADO.
     *
     * @param q           texto normalizado contenido en el nombre
     * @param area        strArea en minúsculas ("moroccan")
     * @param origen      PROPIA o THEMEALDB; vacío para las dos
     * @param franja      constante de TipoComida
     * @param ingrediente texto normalizado contenido en el nombre original de un ingrediente o en el alimento emparejado
     */
    @Query(value = """
            SELECT r.* FROM receta r
             WHERE ((r.visibilidad = 'PUBLICO' AND r.publicada) OR r.propietario_id = :usuarioId)
               AND (:q = '' OR r.nombre_normalizado LIKE '%' || :q || '%')
               AND (CAST(:categoriaId AS uuid) IS NULL OR r.categoria_id = :categoriaId)
               AND (:area = '' OR lower(r.area) = :area)
               AND (:origen = '' OR r.origen = :origen)
               AND (:franja = '' OR :franja = ANY (r.franjas_aptas))
               AND (:ingrediente = '' OR EXISTS (
                     SELECT 1 FROM receta_ingrediente i LEFT JOIN alimento a ON a.id = i.alimento_id
                      WHERE i.receta_id = r.id
                        AND (lower(i.nombre_original) LIKE '%' || :ingrediente || '%'
                             OR a.nombre_normalizado LIKE '%' || :ingrediente || '%')))
               AND (CAST(:maxKcal AS numeric) IS NULL OR (r.estado_nutricional IN ('COMPLETO', 'APROXIMADO')
                     AND r.kcal_racion <= :maxKcal))
               AND (CAST(:minProteina AS numeric) IS NULL OR (r.estado_nutricional IN ('COMPLETO', 'APROXIMADO')
                     AND r.proteina_racion >= :minProteina))
             ORDER BY r.nombre_normalizado, r.id
            """,
            countQuery = """
            SELECT count(*) FROM receta r
             WHERE ((r.visibilidad = 'PUBLICO' AND r.publicada) OR r.propietario_id = :usuarioId)
               AND (:q = '' OR r.nombre_normalizado LIKE '%' || :q || '%')
               AND (CAST(:categoriaId AS uuid) IS NULL OR r.categoria_id = :categoriaId)
               AND (:area = '' OR lower(r.area) = :area)
               AND (:origen = '' OR r.origen = :origen)
               AND (:franja = '' OR :franja = ANY (r.franjas_aptas))
               AND (:ingrediente = '' OR EXISTS (
                     SELECT 1 FROM receta_ingrediente i LEFT JOIN alimento a ON a.id = i.alimento_id
                      WHERE i.receta_id = r.id
                        AND (lower(i.nombre_original) LIKE '%' || :ingrediente || '%'
                             OR a.nombre_normalizado LIKE '%' || :ingrediente || '%')))
               AND (CAST(:maxKcal AS numeric) IS NULL OR (r.estado_nutricional IN ('COMPLETO', 'APROXIMADO')
                     AND r.kcal_racion <= :maxKcal))
               AND (CAST(:minProteina AS numeric) IS NULL OR (r.estado_nutricional IN ('COMPLETO', 'APROXIMADO')
                     AND r.proteina_racion >= :minProteina))
            """,
            nativeQuery = true)
    Page<Receta> buscarCatalogo(@Param("q") String q, @Param("categoriaId") UUID categoriaId,
            @Param("area") String area, @Param("origen") String origen, @Param("franja") String franja,
            @Param("ingrediente") String ingrediente, @Param("maxKcal") BigDecimal maxKcal,
            @Param("minProteina") BigDecimal minProteina, @Param("usuarioId") UUID usuarioId, Pageable pageable);

    /**
     * Hasta {@code limite} recetas relacionadas: de la misma categoría o con el mismo primer ingrediente (por
     * alimento emparejado o por nombre original), visibles para el usuario y sin contar la propia receta.
     */
    @Query(value = """
            SELECT r.* FROM receta r
             WHERE r.id <> :id
               AND ((r.visibilidad = 'PUBLICO' AND r.publicada) OR r.propietario_id = :usuarioId)
               AND (r.categoria_id = CAST(:categoriaId AS uuid)
                    OR EXISTS (SELECT 1 FROM receta_ingrediente i
                                WHERE i.receta_id = r.id AND i.orden = 1
                                  AND (i.alimento_id = CAST(:alimentoId AS uuid)
                                       OR lower(i.nombre_original) = :primerIngrediente)))
             ORDER BY r.nombre_normalizado, r.id
             LIMIT :limite
            """, nativeQuery = true)
    List<Receta> relacionadas(@Param("id") UUID id, @Param("categoriaId") UUID categoriaId,
            @Param("alimentoId") UUID alimentoId, @Param("primerIngrediente") String primerIngrediente,
            @Param("usuarioId") UUID usuarioId, @Param("limite") int limite);

    /** Las recetas ya importadas de entre esos idMeal (para no ofrecerlas como pendientes). */
    List<Receta> findByOrigenAndReferenciaExternaIn(OrigenReceta origen, Collection<String> referenciasExternas);

    /** Para no cargar dos veces las recetas de desarrollo. */
    boolean existsByOrigen(OrigenReceta origen);

    /** Si algún ingrediente usa el alimento (entonces no se puede borrar: se desactiva). */
    @Query("select count(i) > 0 from RecetaIngrediente i where i.alimento.id = :alimentoId")
    boolean alimentoEnUso(@Param("alimentoId") UUID alimentoId);
}
