package com.nutrisport.comidas.repositorio;

import com.nutrisport.comidas.entidad.AnalisisImagen;
import com.nutrisport.comidas.entidad.EstadoAnalisis;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a la tabla {@code analisis_imagen}. Los análisis de otro usuario no se encuentran (404). */
public interface AnalisisImagenRepositorio extends JpaRepository<AnalisisImagen, UUID> {

    Optional<AnalisisImagen> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    /**
     * Análisis en alguno de esos estados creados antes de un instante: con PENDIENTE y PROCESANDO, los
     * atascados (p. ej. tras un reinicio) que la tarea programada marca como FALLIDO.
     */
    List<AnalisisImagen> findByEstadoInAndCreadoEnBefore(Collection<EstadoAnalisis> estados, OffsetDateTime antesDe);

    /** Análisis con imágenes cuya retención general o terminal ha vencido, en orden estable por lotes. */
    @Query("""
            SELECT analisis FROM AnalisisImagen analisis
            WHERE (analisis.imagenKey IS NOT NULL OR analisis.miniaturaKey IS NOT NULL)
              AND (analisis.creadoEn < :limiteRetencion
                   OR (analisis.estado IN :estadosTerminales AND analisis.actualizadoEn < :limiteTerminal))
            ORDER BY analisis.creadoEn, analisis.id
            """)
    List<AnalisisImagen> buscarParaLimpieza(@Param("limiteRetencion") OffsetDateTime limiteRetencion,
            @Param("limiteTerminal") OffsetDateTime limiteTerminal,
            @Param("estadosTerminales") Collection<EstadoAnalisis> estadosTerminales, Pageable pageable);

    /** Todos los análisis del usuario, del más antiguo al más reciente (exportación de datos). */
    List<AnalisisImagen> findByUsuarioIdOrderByCreadoEnAsc(UUID usuarioId);
}
