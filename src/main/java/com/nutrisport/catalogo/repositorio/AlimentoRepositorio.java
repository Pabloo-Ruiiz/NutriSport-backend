package com.nutrisport.catalogo.repositorio;

import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.Preparacion;
import com.nutrisport.catalogo.entidad.Visibilidad;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a la tabla {@code alimento}. */
public interface AlimentoRepositorio extends JpaRepository<Alimento, UUID> {

    boolean existsByCategoriaId(UUID categoriaId);

    /** El alimento con ese código de barras (único si existe). */
    Optional<Alimento> findByCodigoBarras(String codigoBarras);

    /** Los alimentos con alguno de esos códigos (para saber qué resultados de búsqueda ya están en el catálogo). */
    List<Alimento> findByCodigoBarrasIn(Collection<String> codigosBarras);

    /**
     * Todo el catálogo para la administración, incluidos los alimentos privados de los usuarios y los
     * desactivados. Todos los filtros son opcionales; {@code q} llega ya normalizado (vacío = sin filtro,
     * nunca nulo: PostgreSQL no sabría el tipo del parámetro dentro del LIKE).
     */
    @Query(value = """
            select a from Alimento a
             where (:q = '' or a.nombreNormalizado like concat('%', :q, '%'))
               and (:fuente is null or a.fuente = :fuente)
               and (:revisados is null or a.alergenosRevisados = :revisados)
               and (:visibilidad is null or a.visibilidad = :visibilidad)
               and (:activo is null or a.activo = :activo)
            """,
            countQuery = """
            select count(a) from Alimento a
             where (:q = '' or a.nombreNormalizado like concat('%', :q, '%'))
               and (:fuente is null or a.fuente = :fuente)
               and (:revisados is null or a.alergenosRevisados = :revisados)
               and (:visibilidad is null or a.visibilidad = :visibilidad)
               and (:activo is null or a.activo = :activo)
            """)
    Page<Alimento> buscarParaAdmin(@Param("q") String q, @Param("fuente") FuenteDato fuente,
            @Param("revisados") Boolean revisados, @Param("visibilidad") Visibilidad visibilidad,
            @Param("activo") Boolean activo, Pageable pageable);

    /** Parecido (pg_trgm) entre el nombre normalizado de un alimento y un texto. */
    interface SimilitudAlimento {

        UUID getId();

        Number getSimilitud();
    }

    /** Parecido de cada uno de esos alimentos con {@code q} (ya normalizado), de 0 a 1. */
    @Query(value = "SELECT a.id AS id, similarity(a.nombre_normalizado, :q) AS similitud FROM alimento a WHERE a.id IN (:ids)",
            nativeQuery = true)
    List<SimilitudAlimento> similitudes(@Param("q") String q, @Param("ids") Collection<UUID> ids);

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

    /**
     * Los últimos alimentos distintos que el usuario ha registrado en sus comidas (el más reciente
     * primero), entre los que sigue pudiendo usar: activos y públicos o propios.
     */
    @Query(value = """
            SELECT a.*
              FROM alimento a
              JOIN (SELECT ar.alimento_id, max(ar.creado_en) AS ultimo_uso
                      FROM alimento_registrado ar
                      JOIN registro_comida rc ON rc.id = ar.registro_comida_id
                     WHERE rc.usuario_id = :usuarioId
                     GROUP BY ar.alimento_id) uso ON uso.alimento_id = a.id
             WHERE a.activo = true
               AND (a.visibilidad = 'PUBLICO' OR a.propietario_id = :usuarioId)
             ORDER BY uso.ultimo_uso DESC, a.id
             LIMIT :limite
            """, nativeQuery = true)
    List<Alimento> recientes(@Param("usuarioId") UUID usuarioId, @Param("limite") int limite);

    /**
     * Los alimentos que el usuario ha registrado más veces en comidas desde {@code desde} (incluida);
     * a igualdad de veces, el usado más recientemente primero. Solo activos y públicos o propios.
     */
    @Query(value = """
            SELECT a.*
              FROM alimento a
              JOIN (SELECT ar.alimento_id, count(*) AS veces, max(ar.creado_en) AS ultimo_uso
                      FROM alimento_registrado ar
                      JOIN registro_comida rc ON rc.id = ar.registro_comida_id
                     WHERE rc.usuario_id = :usuarioId AND rc.fecha >= :desde
                     GROUP BY ar.alimento_id) uso ON uso.alimento_id = a.id
             WHERE a.activo = true
               AND (a.visibilidad = 'PUBLICO' OR a.propietario_id = :usuarioId)
             ORDER BY uso.veces DESC, uso.ultimo_uso DESC, a.id
             LIMIT :limite
            """, nativeQuery = true)
    List<Alimento> frecuentes(@Param("usuarioId") UUID usuarioId, @Param("desde") LocalDate desde,
            @Param("limite") int limite);
}
