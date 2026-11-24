package com.nutrisport.actividad.repositorio;

import com.nutrisport.actividad.entidad.EstadoEntrenamiento;
import com.nutrisport.actividad.entidad.Entrenamiento;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a la tabla {@code entrenamiento}. Las consultas de un usuario filtran siempre por él: un
 * entrenamiento de otro no se encuentra (404, §8.2). Los rangos de tiempo son [desde, hasta): para un
 * día, desde su inicio hasta el inicio del siguiente en la zona horaria de la aplicación.
 */
public interface EntrenamientoRepositorio extends JpaRepository<Entrenamiento, UUID> {

    @EntityGraph(attributePaths = "tipoActividad")
    Optional<Entrenamiento> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    /** Entrenamientos del usuario en [desde, hasta), en orden cronológico. */
    @EntityGraph(attributePaths = "tipoActividad")
    @Query("""
            select e from Entrenamiento e
             where e.usuario.id = :usuarioId and e.fechaHora >= :desde and e.fechaHora < :hasta
             order by e.fechaHora asc
            """)
    List<Entrenamiento> buscarEntre(@Param("usuarioId") UUID usuarioId, @Param("desde") OffsetDateTime desde,
            @Param("hasta") OffsetDateTime hasta);

    /** Gasto neto de los entrenamientos que cuentan (planificados y realizados) en [desde, hasta). */
    @Query("""
            select coalesce(sum(e.kcalEstimadas), 0) from Entrenamiento e
             where e.usuario.id = :usuarioId and e.fechaHora >= :desde and e.fechaHora < :hasta
               and e.estado in (com.nutrisport.actividad.entidad.EstadoEntrenamiento.PLANIFICADO,
                                com.nutrisport.actividad.entidad.EstadoEntrenamiento.REALIZADO)
            """)
    BigDecimal sumarKcalQueCuentan(@Param("usuarioId") UUID usuarioId, @Param("desde") OffsetDateTime desde,
            @Param("hasta") OffsetDateTime hasta);

    /** Para la tarea nocturna: entrenamientos de todos los usuarios en un estado y anteriores a un instante. */
    List<Entrenamiento> findByEstadoAndFechaHoraBefore(EstadoEntrenamiento estado, OffsetDateTime antesDe);
}
