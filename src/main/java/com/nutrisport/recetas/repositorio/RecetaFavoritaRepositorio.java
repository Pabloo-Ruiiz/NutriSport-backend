package com.nutrisport.recetas.repositorio;

import com.nutrisport.recetas.entidad.RecetaFavorita;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Recetas favoritas de cada usuario (§6.1). */
public interface RecetaFavoritaRepositorio extends JpaRepository<RecetaFavorita, RecetaFavorita.Id> {

    /** Favoritas del usuario con su receta, las más recientes primero. */
    @EntityGraph(attributePaths = "receta")
    List<RecetaFavorita> findByIdUsuarioIdOrderByCreadoEnDesc(UUID usuarioId);

    /** Ids de las recetas favoritas del usuario de entre esas (para marcar {@code favorita} en un listado). */
    @Query("select f.id.recetaId from RecetaFavorita f where f.id.usuarioId = :usuarioId and f.id.recetaId in :recetas")
    Set<UUID> favoritasDe(@Param("usuarioId") UUID usuarioId, @Param("recetas") Collection<UUID> recetas);
}
