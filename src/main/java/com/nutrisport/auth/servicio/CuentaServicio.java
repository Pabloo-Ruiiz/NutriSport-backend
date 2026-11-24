package com.nutrisport.auth.servicio;

import static com.nutrisport.comun.util.Transacciones.despuesDelCommit;

import com.nutrisport.auth.dto.ActualizarCuentaRequest;
import com.nutrisport.auth.dto.CambiarEmailRequest;
import com.nutrisport.auth.dto.CambiarPasswordRequest;
import com.nutrisport.auth.dto.CuentaResponse;
import com.nutrisport.auth.entidad.TipoToken;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.mapper.CuentaMapper;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.comun.correo.CorreoServicio;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de la cuenta del usuario autenticado (§3.A y §7.1) y del consentimiento de datos de salud
 * (§8.3). Todas las operaciones actúan sobre el usuario del token, nunca sobre un id recibido.
 */
@Service
@Transactional
public class CuentaServicio {

    static final Duration VALIDEZ_CAMBIO_EMAIL = Duration.ofHours(24);
    static final String PASSWORD_INCORRECTA = "La contraseña no es correcta";

    private final UsuarioRepositorio usuarioRepositorio;
    private final SeguridadUtils seguridadUtils;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenServicio refreshTokenServicio;
    private final TokenUsuarioServicio tokenUsuarioServicio;
    private final CorreoServicio correoServicio;
    private final CuentaMapper cuentaMapper;
    private final String versionConsentimiento;

    public CuentaServicio(UsuarioRepositorio usuarioRepositorio, SeguridadUtils seguridadUtils,
            PasswordEncoder passwordEncoder, RefreshTokenServicio refreshTokenServicio,
            TokenUsuarioServicio tokenUsuarioServicio, CorreoServicio correoServicio, CuentaMapper cuentaMapper,
            @Value("${app.consentimiento.version}") String versionConsentimiento) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.seguridadUtils = seguridadUtils;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenServicio = refreshTokenServicio;
        this.tokenUsuarioServicio = tokenUsuarioServicio;
        this.correoServicio = correoServicio;
        this.cuentaMapper = cuentaMapper;
        this.versionConsentimiento = versionConsentimiento;
    }

    @Transactional(readOnly = true)
    public CuentaResponse obtener() {
        return cuentaMapper.aRespuesta(usuarioActual());
    }

    /** Actualiza solo los campos enviados (nombre y unidades). */
    public CuentaResponse actualizar(ActualizarCuentaRequest peticion) {
        Usuario usuario = usuarioActual();
        if (peticion.nombre() != null) {
            usuario.setNombre(peticion.nombre().trim());
        }
        if (peticion.unidades() != null) {
            usuario.setUnidades(peticion.unidades());
        }
        return cuentaMapper.aRespuesta(usuario);
    }

    /** Cambia la contraseña tras comprobar la actual y cierra todas las sesiones abiertas. */
    public void cambiarPassword(CambiarPasswordRequest peticion) {
        Usuario usuario = usuarioActual();
        comprobarPassword(usuario, peticion.passwordActual(), "passwordActual");
        usuario.setPasswordHash(passwordEncoder.encode(peticion.nuevaPassword()));
        // La revocación vacía el contexto de persistencia: el cambio de contraseña se guarda antes
        refreshTokenServicio.revocarTodos(usuario.getId());
    }

    /**
     * Envía al email nuevo un enlace (24 h) para confirmar el cambio. Si el email ya está en uso no
     * envía nada, pero responde igual, para no revelar qué emails están registrados.
     */
    public void solicitarCambioEmail(CambiarEmailRequest peticion) {
        Usuario usuario = usuarioActual();
        comprobarPassword(usuario, peticion.password(), "password");

        String nuevoEmail = peticion.nuevoEmail().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepositorio.existsByEmail(nuevoEmail)) {
            return;
        }
        String nombre = usuario.getNombre();
        String token = tokenUsuarioServicio.crear(usuario, TipoToken.CAMBIO_EMAIL, VALIDEZ_CAMBIO_EMAIL, nuevoEmail);
        despuesDelCommit(() -> correoServicio.enviarCambioEmail(nuevoEmail, nombre, token));
    }

    /**
     * Revoca el consentimiento de datos de salud (§8.3): el InterceptorConsentimiento bloquea desde
     * ese momento perfil, comidas, análisis, entrenamientos y seguimiento.
     */
    public void revocarConsentimiento() {
        Usuario usuario = usuarioActual();
        if (usuario.getConsentimientoSaludRevocadoEn() == null) {
            usuario.setConsentimientoSaludRevocadoEn(ahora());
        }
    }

    /** Vuelve a dar el consentimiento con la versión vigente del texto. */
    public void darConsentimiento() {
        Usuario usuario = usuarioActual();
        usuario.setConsentimientoSaludRevocadoEn(null);
        usuario.setConsentimientoSaludEn(ahora());
        usuario.setConsentimientoSaludVersion(versionConsentimiento);
    }

    private Usuario usuarioActual() {
        return usuarioRepositorio.findById(seguridadUtils.usuarioActualId())
                .orElseThrow(() -> new RecursoNoEncontradoException("La cuenta no existe"));
    }

    private void comprobarPassword(Usuario usuario, String password, String campo) {
        if (password == null || !passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new PeticionInvalidaException(PASSWORD_INCORRECTA,
                    List.of(new ErrorCampo(campo, PASSWORD_INCORRECTA)));
        }
    }

    private static OffsetDateTime ahora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
