package com.nutrisport.asistente.repositorio;

import com.nutrisport.asistente.entidad.MensajeIa;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Mensajes de las conversaciones con el asistente (§6.1). */
public interface MensajeIaRepositorio extends JpaRepository<MensajeIa, UUID> {

    /**
     * Todos los mensajes de una conversación en orden cronológico (detalle). Si dos coinciden en el instante (el
     * mensaje y su respuesta segura se guardan juntos), primero el del usuario.
     */
    @Query("""
            select m from MensajeIa m
             where m.conversacion.id = :conversacionId
             order by m.creadoEn asc,
                      case when m.rol = com.nutrisport.asistente.entidad.RolMensaje.USUARIO then 0 else 1 end asc,
                      m.id asc
            """)
    List<MensajeIa> deConversacion(@Param("conversacionId") UUID conversacionId);

    /** Los más recientes primero (para {@link #ultimos} y {@link #ultimo}). */
    @Query("""
            select m from MensajeIa m
             where m.conversacion.id = :conversacionId
             order by m.creadoEn desc,
                      case when m.rol = com.nutrisport.asistente.entidad.RolMensaje.USUARIO then 0 else 1 end desc,
                      m.id desc
            """)
    List<MensajeIa> recientes(@Param("conversacionId") UUID conversacionId, Pageable pageable);

    /** Los últimos {@code n} mensajes de una conversación en orden cronológico (historial para el modelo). */
    default List<MensajeIa> ultimos(UUID conversacionId, int n) {
        List<MensajeIa> mensajes = new ArrayList<>(recientes(conversacionId, PageRequest.of(0, n)));
        Collections.reverse(mensajes);
        return mensajes;
    }

    /** El último mensaje (vista previa de la conversación). */
    default Optional<MensajeIa> ultimo(UUID conversacionId) {
        return recientes(conversacionId, PageRequest.of(0, 1)).stream().findFirst();
    }

    /** Un mensaje de una conversación del usuario; vacío si es de otro (valoración). */
    @Query("select m from MensajeIa m where m.id = :id and m.conversacion.usuario.id = :usuarioId")
    Optional<MensajeIa> buscarDelUsuario(@Param("id") UUID id, @Param("usuarioId") UUID usuarioId);
}
