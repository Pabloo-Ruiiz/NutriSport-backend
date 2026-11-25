package com.nutrisport.perfil.repositorio;

import com.nutrisport.perfil.entidad.ObjetivoDiario;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la tabla {@code objetivo_diario}: un objetivo por usuario y fecha. */
public interface ObjetivoDiarioRepositorio extends JpaRepository<ObjetivoDiario, UUID> {

    Optional<ObjetivoDiario> findByUsuarioIdAndFecha(UUID usuarioId, LocalDate fecha);

    /** Objetivos de un rango de fechas (ambas incluidas), para el seguimiento y la adherencia. */
    List<ObjetivoDiario> findByUsuarioIdAndFechaBetweenOrderByFechaAsc(UUID usuarioId, LocalDate desde,
            LocalDate hasta);

    /** Objetivos desde una fecha: al cambiar el perfil solo se recalculan hoy y los días futuros. */
    List<ObjetivoDiario> findByUsuarioIdAndFechaGreaterThanEqual(UUID usuarioId, LocalDate desde);

    /** Todos los objetivos del usuario, del más antiguo al más reciente (exportación de datos). */
    List<ObjetivoDiario> findByUsuarioIdOrderByFechaAsc(UUID usuarioId);
}
