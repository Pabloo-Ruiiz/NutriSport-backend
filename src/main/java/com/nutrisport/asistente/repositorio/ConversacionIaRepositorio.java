package com.nutrisport.asistente.repositorio;

import com.nutrisport.asistente.entidad.ConversacionIa;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Conversaciones con el asistente (§6.1). Todas las consultas filtran por el usuario: las de otro no existen. */
public interface ConversacionIaRepositorio extends JpaRepository<ConversacionIa, UUID> {

    Optional<ConversacionIa> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    /**
     * Conversaciones del usuario, la del último mensaje más reciente primero (las que aún no tienen mensajes van al
     * final, por fecha de creación). Con {@code archivada} nulo, todas.
     */
    @Query(value = """
            select c from ConversacionIa c
             where c.usuario.id = :usuarioId and (:archivada is null or c.archivada = :archivada)
             order by c.ultimoMensajeEn desc nulls last, c.creadoEn desc, c.id
            """,
            countQuery = """
            select count(c) from ConversacionIa c
             where c.usuario.id = :usuarioId and (:archivada is null or c.archivada = :archivada)
            """)
    Page<ConversacionIa> buscar(@Param("usuarioId") UUID usuarioId, @Param("archivada") Boolean archivada,
            Pageable pageable);
}
