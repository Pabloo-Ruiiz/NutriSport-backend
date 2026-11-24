package com.nutrisport.catalogo.repositorio;

import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.Preparacion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a la tabla {@code alimento}. */
public interface AlimentoRepositorio extends JpaRepository<Alimento, UUID> {

    /**
     * Alimento público con ese nombre, marca y preparación (clave de la importación por CSV). Una
     * marca nula solo coincide con otra nula. Nunca devuelve alimentos privados de usuarios.
     */
    @Query("""
            select a from Alimento a
             where a.visibilidad = com.nutrisport.catalogo.entidad.Visibilidad.PUBLICO
               and a.nombre = :nombre
               and a.preparacion = :preparacion
               and ((:marca is null and a.marca is null) or a.marca = :marca)
            """)
    Optional<Alimento> buscarPublico(@Param("nombre") String nombre, @Param("marca") String marca,
            @Param("preparacion") Preparacion preparacion);

    /**
     * Búsqueda tolerante a errores de escritura con pg_trgm entre los alimentos activos que el usuario
     * puede ver (públicos o propios), opcionalmente de una categoría. Coincide si el nombre se parece
     * ({@code %}, umbral por defecto de pg_trgm 0,3) o lo contiene; los más parecidos van primero.
     * <p>
     * {@code q} debe llegar ya normalizado con TextoNormalizado. El orden lo fija la consulta: el
     * {@link Pageable} debe ir sin ordenación.
     */
    @Query(value = """
            SELECT a.*
              FROM alimento a
             WHERE a.activo = true
               AND (a.visibilidad = 'PUBLICO' OR a.propietario_id = :usuarioId)
               AND (CAST(:categoriaId AS uuid) IS NULL OR a.categoria_id = :categoriaId)
               AND (a.nombre_normalizado % :q OR a.nombre_normalizado LIKE '%' || :q || '%')
             ORDER BY similarity(a.nombre_normalizado, :q) DESC, a.nombre_normalizado, a.id
            """,
            countQuery = """
            SELECT count(*)
              FROM alimento a
             WHERE a.activo = true
               AND (a.visibilidad = 'PUBLICO' OR a.propietario_id = :usuarioId)
               AND (CAST(:categoriaId AS uuid) IS NULL OR a.categoria_id = :categoriaId)
               AND (a.nombre_normalizado % :q OR a.nombre_normalizado LIKE '%' || :q || '%')
            """,
            nativeQuery = true)
    Page<Alimento> buscar(@Param("q") String q, @Param("categoriaId") UUID categoriaId,
            @Param("usuarioId") UUID usuarioId, Pageable pageable);
}
