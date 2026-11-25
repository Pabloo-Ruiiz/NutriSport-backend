package com.nutrisport.comidas.repositorio;

import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.comidas.entidad.RegistroComida;
import com.nutrisport.comidas.entidad.TipoComida;
import java.math.BigDecimal;
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
    // ---------------------------------------------------------------------------------------------
    // Estadísticas (§3.F): consultas agregadas con proyecciones de interfaz
    // ---------------------------------------------------------------------------------------------

    /** Totales de las comidas de un día y cuántas hay. */
    interface TotalesDia {
        LocalDate getFecha();

        BigDecimal getKcal();

        BigDecimal getProteina();

        BigDecimal getCarbohidratos();

        BigDecimal getGrasa();

        Long getRegistros();
    }

    /** Comidas registradas un día de la semana (1 = lunes … 7 = domingo, ISO 8601). */
    interface RegistrosDiaSemana {
        Integer getDiaSemana();

        Long getRegistros();
    }

    /** Un alimento con las veces que aparece en las comidas y las kcal que ha aportado. */
    interface AlimentoConsumido {
        UUID getAlimentoId();

        String getNombre();

        FuenteDato getFuente();

        Long getVeces();

        BigDecimal getKcal();
    }

    /** Totales de las comidas de un tipo y cuántas hay. */
    interface TotalesTipoComida {
        TipoComida getTipoComida();

        BigDecimal getKcal();

        BigDecimal getProteina();

        BigDecimal getCarbohidratos();

        BigDecimal getGrasa();

        Long getRegistros();
    }

    /** Totales por fecha entre dos fechas (ambas incluidas); solo los días con alguna comida, en orden. */
    @Query("""
            select r.fecha as fecha, sum(r.totalKcal) as kcal, sum(r.totalProteina) as proteina,
                   sum(r.totalCarbohidratos) as carbohidratos, sum(r.totalGrasa) as grasa, count(r) as registros
              from RegistroComida r
             where r.usuario.id = :usuarioId and r.fecha between :desde and :hasta
             group by r.fecha
             order by r.fecha
            """)
    List<TotalesDia> totalesPorFecha(@Param("usuarioId") UUID usuarioId, @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    /** Número de comidas por día de la semana entre dos fechas; solo los días de la semana con alguna. */
    @Query(value = """
            SELECT CAST(EXTRACT(ISODOW FROM r.fecha) AS integer) AS "diaSemana", count(*) AS "registros"
              FROM registro_comida r
             WHERE r.usuario_id = :usuarioId AND r.fecha BETWEEN :desde AND :hasta
             GROUP BY 1
             ORDER BY 1
            """, nativeQuery = true)
    List<RegistrosDiaSemana> registrosPorDiaSemana(@Param("usuarioId") UUID usuarioId,
            @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /**
     * Alimentos más consumidos entre dos fechas: más veces primero; a igualdad, más kcal y después por nombre. El
     * número de resultados lo fija {@code limite} (p. ej. {@code PageRequest.of(0, 10)}).
     */
    @Query("""
            select al.id as alimentoId, al.nombre as nombre, al.fuente as fuente, count(a) as veces,
                   sum(a.kcal) as kcal
              from AlimentoRegistrado a join a.alimento al join a.registroComida r
             where r.usuario.id = :usuarioId and r.fecha between :desde and :hasta
             group by al.id, al.nombre, al.fuente
             order by count(a) desc, sum(a.kcal) desc, al.nombre asc
            """)
    List<AlimentoConsumido> alimentosMasConsumidos(@Param("usuarioId") UUID usuarioId,
            @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta, Pageable limite);

    /** Totales por tipo de comida entre dos fechas; solo los tipos con alguna comida. */
    @Query("""
            select r.tipoComida as tipoComida, sum(r.totalKcal) as kcal, sum(r.totalProteina) as proteina,
                   sum(r.totalCarbohidratos) as carbohidratos, sum(r.totalGrasa) as grasa, count(r) as registros
              from RegistroComida r
             where r.usuario.id = :usuarioId and r.fecha between :desde and :hasta
             group by r.tipoComida
            """)
    List<TotalesTipoComida> totalesPorTipoComida(@Param("usuarioId") UUID usuarioId,
            @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}
