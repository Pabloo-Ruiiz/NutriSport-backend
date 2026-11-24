package com.nutrisport.comidas.repositorio;

import com.nutrisport.comidas.entidad.RegistroComida;
import com.nutrisport.comidas.entidad.TipoComida;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a la tabla {@code registro_comida}. Todas las consultas filtran por el usuario: una comida de
 * otro usuario simplemente no se encuentra (404, §8.2).
 */
public interface RegistroComidaRepositorio extends JpaRepository<RegistroComida, UUID> {

    /** Comida del usuario con sus alimentos y los alimentos del catálogo, en una sola consulta. */
    @EntityGraph(attributePaths = {"alimentos", "alimentos.alimento"})
    Optional<RegistroComida> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    /**
     * Comidas de un día con sus alimentos, en orden cronológico (las que no tienen hora, al final del
     * día y por orden de registro).
     */
    @EntityGraph(attributePaths = {"alimentos", "alimentos.alimento"})
    @Query("""
            select r from RegistroComida r
             where r.usuario.id = :usuarioId and r.fecha = :fecha
             order by r.hora asc nulls last, r.creadoEn asc
            """)
    List<RegistroComida> findByUsuarioIdAndFecha(@Param("usuarioId") UUID usuarioId, @Param("fecha") LocalDate fecha);

    /**
     * Historial entre dos fechas (ambas incluidas), opcionalmente de un tipo de comida, en orden
     * cronológico. Sin alimentos: en el listado bastan los totales, y paginar trayendo una colección
     * obligaría a Hibernate a paginar en memoria.
     */
    @Query(value = """
            select r from RegistroComida r
             where r.usuario.id = :usuarioId
               and r.fecha between :desde and :hasta
               and (:tipoComida is null or r.tipoComida = :tipoComida)
             order by r.fecha asc, r.hora asc nulls last, r.creadoEn asc
            """,
            countQuery = """
            select count(r) from RegistroComida r
             where r.usuario.id = :usuarioId
               and r.fecha between :desde and :hasta
               and (:tipoComida is null or r.tipoComida = :tipoComida)
            """)
    Page<RegistroComida> buscar(@Param("usuarioId") UUID usuarioId, @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta, @Param("tipoComida") TipoComida tipoComida, Pageable pageable);

    /** Si el alimento aparece en alguna comida, de cualquier usuario. */
    @Query("select count(a) > 0 from AlimentoRegistrado a where a.alimento.id = :alimentoId")
    boolean alimentoEnUso(@Param("alimentoId") UUID alimentoId);
}
