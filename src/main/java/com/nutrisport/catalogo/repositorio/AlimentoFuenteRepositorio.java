package com.nutrisport.catalogo.repositorio;

import com.nutrisport.catalogo.entidad.AlimentoFuente;
import com.nutrisport.catalogo.entidad.FuenteDato;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Procedencia de los alimentos en proveedores externos (§6.1). */
public interface AlimentoFuenteRepositorio extends JpaRepository<AlimentoFuente, UUID> {

    /** La procedencia de un producto ya guardado, con su alimento (para no volver a pedirlo al proveedor). */
    @EntityGraph(attributePaths = "alimento")
    Optional<AlimentoFuente> findByProveedorAndReferenciaExterna(FuenteDato proveedor, String referenciaExterna);
}
