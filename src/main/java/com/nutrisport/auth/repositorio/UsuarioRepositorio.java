package com.nutrisport.auth.repositorio;

import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Usuario;
import java.time.OffsetDateTime;
import java.util.Optional;
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
     * Borra físicamente las cuentas con el estado dado eliminadas antes de {@code limite}. Sus datos
     * (roles, tokens y, más adelante, perfil, comidas...) se borran por las FK con ON DELETE CASCADE.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Usuario u where u.estado = :estado and u.eliminadoEn < :limite")
    int borrarEliminadosAntesDe(@Param("estado") EstadoUsuario estado, @Param("limite") OffsetDateTime limite);
}
