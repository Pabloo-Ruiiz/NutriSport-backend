package com.nutrisport.perfil.repositorio;

import com.nutrisport.perfil.entidad.PerfilDeportivo;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la tabla {@code perfil_deportivo}; cada usuario tiene como mucho un perfil. */
public interface PerfilDeportivoRepositorio extends JpaRepository<PerfilDeportivo, UUID> {

    Optional<PerfilDeportivo> findByUsuarioId(UUID usuarioId);
}
