package com.nutrisport.auth.servicio;

import com.nutrisport.auth.dto.EliminarCuentaRequest;
import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Eliminación de la cuenta del usuario autenticado (§6.3 y §8.3, derecho de supresión).
 * <p>
 * Inmediata: se borra el usuario y la base de datos borra sus datos por las FK con ON DELETE
 * CASCADE. Diferida: queda en estado ELIMINADO (ya no puede iniciar sesión) y
 * {@link PurgaCuentasTarea} la borra físicamente a los 30 días.
 */
@Service
@Transactional
public class EliminacionCuentaServicio {

    private static final String PASSWORD_INCORRECTA = "La contraseña no es correcta";

    private final UsuarioRepositorio usuarioRepositorio;
    private final SeguridadUtils seguridadUtils;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenServicio refreshTokenServicio;

    public EliminacionCuentaServicio(UsuarioRepositorio usuarioRepositorio, SeguridadUtils seguridadUtils,
            PasswordEncoder passwordEncoder, RefreshTokenServicio refreshTokenServicio) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.seguridadUtils = seguridadUtils;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenServicio = refreshTokenServicio;
    }

    public void eliminar(EliminarCuentaRequest peticion) {
        Usuario usuario = usuarioRepositorio.findById(seguridadUtils.usuarioActualId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La cuenta no existe"));
        if (!passwordEncoder.matches(peticion.password(), usuario.getPasswordHash())) {
            throw new PeticionInvalidaException(PASSWORD_INCORRECTA,
                    List.of(new ErrorCampo("password", PASSWORD_INCORRECTA)));
        }
        UUID id = usuario.getId();

        if (!peticion.inmediata()) {
            usuario.setEstado(EstadoUsuario.ELIMINADO);
            usuario.setEliminadoEn(OffsetDateTime.now(ZoneOffset.UTC));
        }
        // La revocación vacía el contexto de persistencia: el cambio de estado se guarda antes
        refreshTokenServicio.revocarTodos(id);

        if (peticion.inmediata()) {
            // TODO paso 18: borrar también las fotos del usuario en el almacenamiento (MinIO/S3);
            // la base de datos no puede borrarlas en cascada
            usuarioRepositorio.deleteById(id);
        }
    }
}
