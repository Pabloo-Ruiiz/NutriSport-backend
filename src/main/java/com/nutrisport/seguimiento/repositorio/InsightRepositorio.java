package com.nutrisport.seguimiento.repositorio;

import com.nutrisport.seguimiento.entidad.Insight;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Insights de cada usuario (§4.6). */
public interface InsightRepositorio extends JpaRepository<Insight, UUID> {

    /** Los vigentes del usuario, los más recientes primero. */
    List<Insight> findByUsuarioIdAndVigenteTrueOrderByCreadoEnDesc(UUID usuarioId);

    /** Deja de mostrar los de detecciones anteriores (antes de guardar los nuevos). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Insight i set i.vigente = false where i.usuario.id = :usuarioId and i.vigente = true")
    int marcarNoVigentes(@Param("usuarioId") UUID usuarioId);

    /** Al desactivar los insights en la cuenta se borran todos los del usuario. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Insight i where i.usuario.id = :usuarioId")
    int borrarDelUsuario(@Param("usuarioId") UUID usuarioId);

    /**
     * Siguiente lote de usuarios a los que la tarea nocturna busca patrones: activos, con los insights activados, con
     * el consentimiento de salud vigente y con alguna comida, entrenamiento o pesaje desde {@code desde}. En orden de
     * id, a partir de {@code despuesDe} (paginación por clave: el primer lote, con el UUID cero).
     *
     * @param desdeInstante inicio de {@code desde} en la zona de la aplicación (para los entrenamientos)
     */
    @Query(value = """
            SELECT u.id FROM usuario u
             WHERE u.estado = 'ACTIVO' AND u.insights_activos AND u.consentimiento_salud_revocado_en IS NULL
               AND u.id > :despuesDe
               AND (EXISTS (SELECT 1 FROM registro_comida r WHERE r.usuario_id = u.id AND r.fecha >= :desde)
                    OR EXISTS (SELECT 1 FROM entrenamiento e
                                WHERE e.usuario_id = u.id AND e.fecha_hora >= :desdeInstante)
                    OR EXISTS (SELECT 1 FROM registro_peso p WHERE p.usuario_id = u.id AND p.fecha >= :desde))
             ORDER BY u.id
             LIMIT :limite
            """, nativeQuery = true)
    List<UUID> usuariosParaDetectar(@Param("desde") LocalDate desde,
            @Param("desdeInstante") OffsetDateTime desdeInstante, @Param("despuesDe") UUID despuesDe,
            @Param("limite") int limite);
}
