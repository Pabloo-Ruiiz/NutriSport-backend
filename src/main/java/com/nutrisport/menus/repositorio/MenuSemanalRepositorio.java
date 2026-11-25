package com.nutrisport.menus.repositorio;

import com.nutrisport.menus.entidad.MenuSemanal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Menús de cada usuario (§6.1). Todas las consultas filtran por el usuario: los menús de otro no existen. */
public interface MenuSemanalRepositorio extends JpaRepository<MenuSemanal, UUID> {

    /** Un menú propio con sus comidas y sus recetas (para el detalle). */
    @EntityGraph(attributePaths = {"comidas", "comidas.receta"})
    Optional<MenuSemanal> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    /**
     * Menús del usuario, el más reciente primero. Los filtros nulos no filtran.
     *
     * @param favorito solo los favoritos (true) o los que no lo son (false)
     * @param desde    solo los que terminan ese día o después (los que aún no han acabado)
     */
    @Query(value = """
            select m from MenuSemanal m
             where m.usuario.id = :usuarioId
               and (:favorito is null or m.favorito = :favorito)
               and (cast(:desde as LocalDate) is null or m.fechaFin >= :desde)
             order by m.fechaInicio desc, m.id
            """,
            countQuery = """
            select count(m) from MenuSemanal m
             where m.usuario.id = :usuarioId
               and (:favorito is null or m.favorito = :favorito)
               and (cast(:desde as LocalDate) is null or m.fechaFin >= :desde)
            """)
    Page<MenuSemanal> buscar(@Param("usuarioId") UUID usuarioId, @Param("favorito") Boolean favorito,
            @Param("desde") LocalDate desde, Pageable pageable);
}
