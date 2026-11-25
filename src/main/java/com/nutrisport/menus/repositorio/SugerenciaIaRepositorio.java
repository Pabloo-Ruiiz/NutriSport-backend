package com.nutrisport.menus.repositorio;

import com.nutrisport.menus.entidad.SugerenciaIa;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Sugerencias de la IA para comidas planificadas (V15). */
public interface SugerenciaIaRepositorio extends JpaRepository<SugerenciaIa, UUID> {

    /** Una sugerencia del usuario para esa comida de ese menú; vacío si es de otro o de otra comida. */
    @Query("""
            select s from SugerenciaIa s
             where s.id = :id and s.usuario.id = :usuarioId
               and s.comida.id = :comidaId and s.comida.menu.id = :menuId
            """)
    Optional<SugerenciaIa> buscarDelUsuario(@Param("id") UUID id, @Param("menuId") UUID menuId,
            @Param("comidaId") UUID comidaId, @Param("usuarioId") UUID usuarioId);
}
