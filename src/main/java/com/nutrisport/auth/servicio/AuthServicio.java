package com.nutrisport.auth.servicio;

import com.nutrisport.auth.dto.LoginRequest;
import com.nutrisport.auth.dto.RegistroRequest;
import com.nutrisport.auth.dto.TokenResponse;
import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.entidad.TipoToken;
import com.nutrisport.auth.entidad.TokenUsuario;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.mapper.CuentaMapper;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.JwtServicio;
import com.nutrisport.auth.seguridad.UsuarioAutenticado;
import com.nutrisport.comun.correo.CorreoServicio;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Registro, verificación de email y login (§3.A, §5.5 y §8.1).
 * <p>
 * Protección contra enumeración: registro y reenvío responden igual exista o no el email, y el login
 * da el mismo error para credenciales incorrectas y para cuentas bloqueadas o eliminadas.
 * Los correos se envían después del commit: si la transacción falla no sale ningún enlace.
 */
@Service
@Transactional
public class AuthServicio {

    static final Duration VALIDEZ_VERIFICACION = Duration.ofHours(24);
    static final String CREDENCIALES_INCORRECTAS = "Email o contraseña incorrectos";

    private final UsuarioRepositorio usuarioRepositorio;
    private final TokenUsuarioServicio tokenUsuarioServicio;
    private final RefreshTokenServicio refreshTokenServicio;
    private final CorreoServicio correoServicio;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtServicio jwtServicio;
    private final CuentaMapper cuentaMapper;
    private final String versionConsentimiento;

    public AuthServicio(UsuarioRepositorio usuarioRepositorio, TokenUsuarioServicio tokenUsuarioServicio,
            RefreshTokenServicio refreshTokenServicio, CorreoServicio correoServicio, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtServicio jwtServicio, CuentaMapper cuentaMapper,
            @Value("${app.consentimiento.version}") String versionConsentimiento) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.tokenUsuarioServicio = tokenUsuarioServicio;
        this.refreshTokenServicio = refreshTokenServicio;
        this.correoServicio = correoServicio;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtServicio = jwtServicio;
        this.cuentaMapper = cuentaMapper;
        this.versionConsentimiento = versionConsentimiento;
    }

    /**
     * Crea la cuenta y envía el enlace de verificación. Si el email ya existe no lanza error: avisa
     * al titular por correo y termina, sin que la respuesta revele nada.
     */
    public void registrar(RegistroRequest peticion) {
        String email = normalizarEmail(peticion.email());
        // Se calcula siempre: así el tiempo de respuesta no delata si el email ya existe
        String passwordHash = passwordEncoder.encode(peticion.password());

        if (usuarioRepositorio.existsByEmail(email)) {
            despuesDelCommit(() -> correoServicio.enviarAvisoCuentaExistente(email));
            return;
        }

        OffsetDateTime ahora = ahora();
        Usuario usuario = new Usuario();
        usuario.setNombre(peticion.nombre().trim());
        usuario.setEmail(email);
        usuario.setPasswordHash(passwordHash);
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.setEmailVerificadoEn(null);
        usuario.setMayorEdadDeclaradaEn(ahora);
        usuario.setConsentimientoSaludEn(ahora);
        usuario.setConsentimientoSaludVersion(versionConsentimiento);
        usuario.anadirRol(Rol.DEPORTISTA);
        usuarioRepositorio.save(usuario);

        enviarVerificacion(usuario);
    }

    /** Consume el token de verificación y marca el email como verificado. */
    public void verificarEmail(String token) {
        TokenUsuario tokenUsuario = tokenUsuarioServicio.consumir(token, TipoToken.VERIFICACION_EMAIL);
        Usuario usuario = tokenUsuario.getUsuario();
        if (usuario.getEmailVerificadoEn() == null) {
            usuario.setEmailVerificadoEn(ahora());
        }
    }

    /** Si la cuenta existe, está activa y no está verificada, envía un enlace nuevo; si no, no hace nada. */
    public void reenviarVerificacion(String email) {
        usuarioRepositorio.findByEmail(normalizarEmail(email))
                .filter(Usuario::estaActivo)
                .filter(usuario -> !usuario.emailVerificado())
                .ifPresent(this::enviarVerificacion);
    }

    /**
     * Autentica con email y contraseña. Se permite sin email verificado (las funciones de IA lo
     * exigirán). Cualquier fallo da {@link BadCredentialsException} con el mismo mensaje.
     */
    public ResultadoLogin login(LoginRequest peticion) {
        UsuarioAutenticado autenticado;
        try {
            Authentication autenticacion = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalizarEmail(peticion.email()),
                            peticion.password()));
            autenticado = (UsuarioAutenticado) autenticacion.getPrincipal();
        } catch (AuthenticationException e) {
            // Credenciales incorrectas, usuario inexistente, bloqueado o eliminado: mismo mensaje
            throw new BadCredentialsException(CREDENCIALES_INCORRECTAS);
        }

        Usuario usuario = usuarioRepositorio.findById(autenticado.getId())
                .orElseThrow(() -> new BadCredentialsException(CREDENCIALES_INCORRECTAS));
        usuario.setUltimoAcceso(ahora());

        String refreshToken = refreshTokenServicio.emitir(usuario, null);
        TokenResponse respuesta = new TokenResponse(jwtServicio.generarAccessToken(autenticado),
                jwtServicio.segundosValidez(), cuentaMapper.aRespuesta(usuario));
        return new ResultadoLogin(respuesta, refreshToken, peticion.mantenerSesion());
    }

    private void enviarVerificacion(Usuario usuario) {
        // Se copian los datos antes de crear el token: crear() desconecta las entidades cargadas
        String email = usuario.getEmail();
        String nombre = usuario.getNombre();
        String token = tokenUsuarioServicio.crear(usuario, TipoToken.VERIFICACION_EMAIL, VALIDEZ_VERIFICACION, null);
        despuesDelCommit(() -> correoServicio.enviarVerificacion(email, nombre, token));
    }

    /** Ejecuta la acción cuando la transacción se confirma; sin transacción, la ejecuta ya. */
    private static void despuesDelCommit(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }

    private static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static OffsetDateTime ahora() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
