package com.nutrisport.auth.repositorio;

import com.nutrisport.auth.entidad.TipoToken;
import com.nutrisport.auth.entidad.TokenUsuario;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a la tabla {@code token_usuario}. */
public interface TokenUsuarioRepositorio extends JpaRepository<TokenUsuario, UUID> {

    /** Bloquea la fila (SELECT ... FOR UPDATE): dos peticiones simultáneas no pueden consumir el mismo token. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TokenUsuario> findByTokenHashAndTipo(String tokenHash, TipoToken tipo);

    /**
     * Invalida los tokens pendientes (sin usar y sin caducar) de un tipo, p. ej. al reenviar el correo
     * de verificación. Se marcan como caducados ahora, no como usados: {@code usadoEn} solo indica un
     * uso real. Devuelve el número de tokens invalidados.
     * <p>
     * Vacía el contexto de persistencia al terminar: las entidades cargadas antes (p. ej. el usuario)
     * quedan desconectadas, así que sus cambios deben hacerse antes de llamar a este método.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update TokenUsuario t
               set t.expiraEn = offset datetime, t.actualizadoEn = offset datetime
             where t.usuario.id = :usuarioId and t.tipo = :tipo
               and t.usadoEn is null and t.expiraEn > offset datetime
            """)
    int invalidarPendientes(@Param("usuarioId") UUID usuarioId, @Param("tipo") TipoToken tipo);
}
