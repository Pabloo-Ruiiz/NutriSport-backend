package com.nutrisport.catalogo.repositorio;

import com.nutrisport.catalogo.entidad.Categoria;
import com.nutrisport.catalogo.entidad.TipoCategoria;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la tabla {@code categoria}. */
public interface CategoriaRepositorio extends JpaRepository<Categoria, UUID> {

    List<Categoria> findByTipoOrderByOrden(TipoCategoria tipo);

    Optional<Categoria> findByTipoAndNombre(TipoCategoria tipo, String nombre);

    boolean existsByPadreId(UUID padreId);

    boolean existsByTipoAndNombreIgnoreCase(TipoCategoria tipo, String nombre);

    boolean existsByTipoAndNombreIgnoreCaseAndIdNot(TipoCategoria tipo, String nombre, UUID id);
}
