package com.nutrisport.auth.repositorio;

import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Usuario;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a la tabla {@code usuario}. El email se pasa siempre ya normalizado en minúsculas. */
public interface UsuarioRepositorio extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Búsqueda para la administración: {@code q} (ya en minúsculas; vacío = sin filtro) contenido en el
     * nombre o el email, opcionalmente de un estado. No se usa un {@code q} nulo: PostgreSQL no sabría
     * el tipo del parámetro dentro del LIKE.
     */
    @Query(value = """
            select u from Usuario u
             where (:q = '' or lower(u.nombre) like concat('%', :q, '%') or u.email like concat('%', :q, '%'))
               and (:estado is null or u.estado = :estado)
            """,
            countQuery = """
            select count(u) from Usuario u
             where (:q = '' or lower(u.nombre) like concat('%', :q, '%') or u.email like concat('%', :q, '%'))
               and (:estado is null or u.estado = :estado)
            """)
    Page<Usuario> buscarParaAdmin(@Param("q") String q, @Param("estado") EstadoUsuario estado, Pageable pageable);

    /**
     * Borra físicamente las cuentas con el estado dado eliminadas antes de {@code limite}. Sus datos
     * (roles, tokens y, más adelante, perfil, comidas...) se borran por las FK con ON DELETE CASCADE.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Usuario u where u.estado = :estado and u.eliminadoEn < :limite")
    int borrarEliminadosAntesDe(@Param("estado") EstadoUsuario estado, @Param("limite") OffsetDateTime limite);
}
