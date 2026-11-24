package com.nutrisport.auth.servicio;

import com.nutrisport.auth.dto.LoginRequest;
import com.nutrisport.auth.dto.RegistroRequest;
import com.nutrisport.auth.dto.RestablecerPasswordRequest;
import com.nutrisport.auth.dto.TokenResponse;
import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.entidad.TipoToken;
import com.nutrisport.auth.entidad.TokenUsuario;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.mapper.CuentaMapper;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.JwtServicio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.auth.seguridad.UsuarioAutenticado;
import com.nutrisport.comun.correo.CorreoServicio;
import com.nutrisport.comun.excepcion.AccesoDenegadoException;
import com.nutrisport.comun.limite.LimitadorPeticiones;
import com.nutrisport.comun.limite.PlanLimite;
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
 * Registro, verificación de email, login, sesiones y recuperación de contraseña (§3.A, §5.5, §8.1).
 * <p>
 * Protección contra enumeración: registro, reenvío y recuperación responden igual exista o no el
 * email, y el login da el mismo error para credenciales incorrectas y cuentas bloqueadas o eliminadas.
 * <p>
 * Límite de intentos (§8.1) con {@link LimitadorPeticiones}: nunca se cambia el estado de la cuenta
 * por intentos fallidos, para que nadie pueda bloquear cuentas ajenas.
 * <p>
 * Los correos se envían después del commit: si la transacción falla no sale ningún enlace.
 */
@Service
@Transactional
public class AuthServicio {

    static final Duration VALIDEZ_VERIFICACION = Duration.ofHours(24);
    static final Duration VALIDEZ_RECUPERACION = Duration.ofMinutes(30);
    static final String CREDENCIALES_INCORRECTAS = "Email o contraseña incorrectos";

    // Prefijos para que cada operación tenga su propio contador aunque compartan plan
    private static final String CLAVE_EMAIL = "email:";
    private static final String CLAVE_IP = "ip:";
    private static final String CLAVE_REGISTRO = "registro:";
    private static final String CLAVE_REENVIO = "reenvio:";

    private final UsuarioRepositorio usuarioRepositorio;
    private final TokenUsuarioServicio tokenUsuarioServicio;
    private final RefreshTokenServicio refreshTokenServicio;
    private final CorreoServicio correoServicio;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtServicio jwtServicio;
    private final CuentaMapper cuentaMapper;
    private final LimitadorPeticiones limitador;
    private final SeguridadUtils seguridadUtils;
    private final String versionConsentimiento;
    private final String urlFrontend;

    public AuthServicio(UsuarioRepositorio usuarioRepositorio, TokenUsuarioServicio tokenUsuarioServicio,
            RefreshTokenServicio refreshTokenServicio, CorreoServicio correoServicio, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtServicio jwtServicio, CuentaMapper cuentaMapper,
            LimitadorPeticiones limitador, SeguridadUtils seguridadUtils,
            @Value("${app.consentimiento.version}") String versionConsentimiento,
            @Value("${app.frontend-url}") String urlFrontend) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.tokenUsuarioServicio = tokenUsuarioServicio;
        this.refreshTokenServicio = refreshTokenServicio;
        this.correoServicio = correoServicio;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtServicio = jwtServicio;
        this.cuentaMapper = cuentaMapper;
        this.limitador = limitador;
        this.seguridadUtils = seguridadUtils;
        this.versionConsentimiento = versionConsentimiento;
        this.urlFrontend = urlFrontend;
    }

    // ---------------------------------------------------------------------------------------------
    // Registro y verificación
    // ---------------------------------------------------------------------------------------------

    /**
     * Crea la cuenta y envía el enlace de verificación. Si el email ya existe no lanza error: avisa
     * al titular por correo y termina, sin que la respuesta revele nada. Máximo 3 por hora por IP.
     */
    public void registrar(RegistroRequest peticion, String ip) {
        limitador.consumir(PlanLimite.REGISTRO, CLAVE_REGISTRO + ip);

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

    /**
     * Si la cuenta existe, está activa y no está verificada, envía un enlace nuevo; si no, no hace
     * nada. Máximo 3 por hora por IP.
     */
    public void reenviarVerificacion(String email, String ip) {
        limitador.consumir(PlanLimite.REGISTRO, CLAVE_REENVIO + ip);
        usuarioRepositorio.findByEmail(normalizarEmail(email))
                .filter(Usuario::estaActivo)
                .filter(usuario -> !usuario.emailVerificado())
                .ifPresent(this::enviarVerificacion);
    }

    // ---------------------------------------------------------------------------------------------
    // Login y sesiones
    // ---------------------------------------------------------------------------------------------

    /**
     * Autentica con email y contraseña. Se permite sin email verificado (las funciones de IA lo
     * exigirán). Cualquier fallo da {@link BadCredentialsException} con el mismo mensaje.
     * <p>
     * Tras 5 fallos en 15 minutos con el mismo email o desde la misma IP responde 429 hasta que se
     * recupere un intento, aunque la contraseña sea correcta. Un login correcto reinicia el contador
     * del email, no el de la IP: si no, un atacante podría reiniciarlo entrando en su propia cuenta.
     */
    public ResultadoLogin login(LoginRequest peticion, String ip) {
        String email = normalizarEmail(peticion.email());
        limitador.comprobarDisponible(PlanLimite.LOGIN_FALLIDO, CLAVE_EMAIL + email);
        limitador.comprobarDisponible(PlanLimite.LOGIN_FALLIDO, CLAVE_IP + ip);

        UsuarioAutenticado autenticado;
        try {
            Authentication autenticacion = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, peticion.password()));
            autenticado = (UsuarioAutenticado) autenticacion.getPrincipal();
        } catch (AuthenticationException e) {
            // Credenciales incorrectas, usuario inexistente, bloqueado o eliminado: mismo mensaje
            limitador.registrarFallo(PlanLimite.LOGIN_FALLIDO, CLAVE_EMAIL + email);
            limitador.registrarFallo(PlanLimite.LOGIN_FALLIDO, CLAVE_IP + ip);
            throw new BadCredentialsException(CREDENCIALES_INCORRECTAS);
        }
        limitador.reiniciar(PlanLimite.LOGIN_FALLIDO, CLAVE_EMAIL + email);

        Usuario usuario = usuarioRepositorio.findById(autenticado.getId())
                .orElseThrow(() -> new BadCredentialsException(CREDENCIALES_INCORRECTAS));
        usuario.setUltimoAcceso(ahora());

        String refreshToken = refreshTokenServicio.emitir(usuario, null, peticion.mantenerSesion());
        return new ResultadoLogin(respuestaToken(usuario), refreshToken, peticion.mantenerSesion());
    }

    /**
     * Rota el refresh token de la cookie y emite un access token nuevo.
     * <p>
     * Si la petición trae cabecera {@code Origin}, debe ser la del frontend (§8.5): la cookie es
     * {@code SameSite=Strict}, y esta comprobación es una segunda barrera contra peticiones de otros sitios.
     * <p>
     * {@code noRollbackFor}: si se detecta la reutilización de un token, la revocación de su familia
     * se guarda aunque se responda 401.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public ResultadoLogin refrescar(String refreshToken, String origen) {
        comprobarOrigen(origen);
        RefreshTokenServicio.Rotacion rotacion = refreshTokenServicio.rotar(refreshToken);
        return new ResultadoLogin(respuestaToken(rotacion.usuario()), rotacion.token(), rotacion.persistente());
    }

    /** Cierra la sesión de la cookie. Sin cookie o con un token desconocido no hace nada. */
    public void logout(String refreshToken) {
        refreshTokenServicio.revocar(refreshToken);
    }

    /** Cierra todas las sesiones del usuario autenticado (los access tokens caducan en 15 min). */
    public void logoutTodas() {
        refreshTokenServicio.revocarTodos(seguridadUtils.usuarioActualId());
    }

    // ---------------------------------------------------------------------------------------------
    // Recuperación de contraseña
    // ---------------------------------------------------------------------------------------------

    /**
     * Si el usuario existe y está activo, envía un enlace de recuperación válido 30 minutos; si no,
     * no hace nada. Máximo 3 por hora por IP.
     */
    public void solicitarRecuperacion(String email, String ip) {
        limitador.consumir(PlanLimite.RECUPERACION, ip);
        usuarioRepositorio.findByEmail(normalizarEmail(email))
                .filter(Usuario::estaActivo)
                .ifPresent(usuario -> {
                    String destino = usuario.getEmail();
                    String nombre = usuario.getNombre();
                    String token = tokenUsuarioServicio.crear(usuario, TipoToken.RECUPERACION_PASSWORD,
                            VALIDEZ_RECUPERACION, null);
                    despuesDelCommit(() -> correoServicio.enviarRecuperacion(destino, nombre, token));
                });
    }

    /** Cambia la contraseña con el token del enlace y cierra todas las sesiones del usuario (HU-03). */
    public void restablecerPassword(RestablecerPasswordRequest peticion) {
        TokenUsuario tokenUsuario = tokenUsuarioServicio.consumir(peticion.token(), TipoToken.RECUPERACION_PASSWORD);
        Usuario usuario = tokenUsuario.getUsuario();
        usuario.setPasswordHash(passwordEncoder.encode(peticion.nuevaPassword()));
        // La revocación vacía el contexto de persistencia: el cambio de contraseña se guarda antes
        refreshTokenServicio.revocarTodos(usuario.getId());
    }

    // ---------------------------------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------------------------------

    private TokenResponse respuestaToken(Usuario usuario) {
        return new TokenResponse(jwtServicio.generarAccessToken(UsuarioAutenticado.de(usuario)),
                jwtServicio.segundosValidez(), cuentaMapper.aRespuesta(usuario));
    }

    private void comprobarOrigen(String origen) {
        if (origen != null && !origen.equals(urlFrontend)) {
            throw new AccesoDenegadoException("Origen no permitido");
        }
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
