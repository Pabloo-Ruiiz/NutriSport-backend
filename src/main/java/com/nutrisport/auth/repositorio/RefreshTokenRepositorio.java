package com.nutrisport.auth.repositorio;

import com.nutrisport.auth.entidad.RefreshToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a la tabla {@code refresh_token}. Las revocaciones masivas no pasan por {@code @PreUpdate},
 * por eso actualizan también {@code actualizadoEn}; devuelven el número de tokens revocados.
 */
public interface RefreshTokenRepositorio extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByFamilia(UUID familia);

    /** Cierra todas las sesiones del usuario. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken r
               set r.revocado = true, r.actualizadoEn = offset datetime
             where r.usuario.id = :usuarioId and r.revocado = false
            """)
    int revocarTodosDeUsuario(@Param("usuarioId") UUID usuarioId);

    /** Revoca toda la familia al detectar la reutilización de un token ya rotado (§5.5). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken r
               set r.revocado = true, r.actualizadoEn = offset datetime
             where r.familia = :familia and r.revocado = false
            """)
    int revocarFamilia(@Param("familia") UUID familia);
}
