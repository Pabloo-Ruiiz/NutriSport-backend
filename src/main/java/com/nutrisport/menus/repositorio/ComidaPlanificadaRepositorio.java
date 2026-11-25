package com.nutrisport.menus.repositorio;

import com.nutrisport.menus.entidad.ComidaPlanificada;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Comidas planificadas (§6.1). */
public interface ComidaPlanificadaRepositorio extends JpaRepository<ComidaPlanificada, UUID> {

    /** Una comida de un menú del usuario, con su menú y su receta; vacío si el menú es de otro. */
    @EntityGraph(attributePaths = {"menu", "receta"})
    @Query("select c from ComidaPlanificada c where c.id = :id and c.menu.id = :menuId and c.menu.usuario.id = :usuarioId")
    Optional<ComidaPlanificada> buscarDelUsuario(@Param("id") UUID id, @Param("menuId") UUID menuId,
            @Param("usuarioId") UUID usuarioId);

    /** Comidas no consumidas de ese día en los menús ACTIVO del usuario, con su menú y su receta. */
    @EntityGraph(attributePaths = {"menu", "receta"})
    @Query("""
            select c from ComidaPlanificada c
             where c.menu.usuario.id = :usuarioId and c.fecha = :fecha and c.consumida = false
               and c.menu.estado = com.nutrisport.menus.entidad.EstadoMenu.ACTIVO
             order by c.menu.fechaInicio, c.menu.id, c.orden
            """)
    List<ComidaPlanificada> pendientesDelDia(@Param("usuarioId") UUID usuarioId, @Param("fecha") LocalDate fecha);

    /** Si alguna comida planificada usa la receta (entonces no se puede borrar). */
    @Query("select count(c) > 0 from ComidaPlanificada c where c.receta.id = :recetaId")
    boolean recetaPlanificada(@Param("recetaId") UUID recetaId);
}
