package com.nutrisport.auth.repositorio;

import com.nutrisport.auth.entidad.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la tabla {@code usuario}. El email se pasa siempre ya normalizado en minúsculas. */
public interface UsuarioRepositorio extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
