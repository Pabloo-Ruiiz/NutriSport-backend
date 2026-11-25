package com.nutrisport.catalogo.repositorio;

import com.nutrisport.catalogo.entidad.MapeoIngrediente;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Emparejamientos de ingredientes (§6.1). */
public interface MapeoIngredienteRepositorio extends JpaRepository<MapeoIngrediente, UUID> {

    @EntityGraph(attributePaths = "alimento")
    Optional<MapeoIngrediente> findByNombreNormalizado(String nombreNormalizado);

    /** Cola de revisión del administrador. */
    @EntityGraph(attributePaths = "alimento")
    Page<MapeoIngrediente> findByRevisado(boolean revisado, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "alimento")
    Page<MapeoIngrediente> findAll(Pageable pageable);
}
