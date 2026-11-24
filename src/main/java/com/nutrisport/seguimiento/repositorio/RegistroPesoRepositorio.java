package com.nutrisport.seguimiento.repositorio;

import com.nutrisport.seguimiento.entidad.RegistroPeso;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la tabla {@code registro_peso}: un pesaje por usuario y día. */
public interface RegistroPesoRepositorio extends JpaRepository<RegistroPeso, UUID> {

    Optional<RegistroPeso> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    Optional<RegistroPeso> findByUsuarioIdAndFecha(UUID usuarioId, LocalDate fecha);

    /** El pesaje más reciente del usuario: su peso actual. */
    Optional<RegistroPeso> findFirstByUsuarioIdOrderByFechaDesc(UUID usuarioId);

    /** Pesajes entre dos fechas (ambas incluidas), en orden cronológico. */
    List<RegistroPeso> findByUsuarioIdAndFechaBetweenOrderByFechaAsc(UUID usuarioId, LocalDate desde, LocalDate hasta);
}
