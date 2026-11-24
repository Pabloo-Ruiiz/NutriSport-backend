package com.nutrisport.perfil.repositorio;

import com.nutrisport.perfil.entidad.PreferenciaAlimentaria;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a la tabla {@code preferencia_alimentaria}. */
public interface PreferenciaAlimentariaRepositorio extends JpaRepository<PreferenciaAlimentaria, UUID> {

    List<PreferenciaAlimentaria> findByUsuarioId(UUID usuarioId);

    /**
     * Borra todas las preferencias del usuario con una sola sentencia (por ejemplo, para
     * sustituirlas por las nuevas); devuelve cuántas se han borrado.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PreferenciaAlimentaria p where p.usuario.id = :usuarioId")
    int deleteByUsuarioId(@Param("usuarioId") UUID usuarioId);
}
