package com.nutrisport.auth.web;

import com.nutrisport.auth.dto.LoginRequest;
import com.nutrisport.auth.dto.RecuperarPasswordRequest;
import com.nutrisport.auth.dto.ReenviarVerificacionRequest;
import com.nutrisport.auth.dto.RegistroRequest;
import com.nutrisport.auth.dto.RestablecerPasswordRequest;
import com.nutrisport.auth.dto.TokenResponse;
import com.nutrisport.auth.dto.VerificarEmailRequest;
import com.nutrisport.auth.servicio.AuthServicio;
import com.nutrisport.auth.servicio.ResultadoLogin;
import com.nutrisport.comun.dto.MensajeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autenticación (§7.1). Todas las operaciones son públicas salvo {@code /logout-todas}; las públicas
 * llevan {@code @SecurityRequirements} vacío para que Swagger no les pida JWT.
 * <p>
 * La IP para el límite de intentos es {@link HttpServletRequest#getRemoteAddr()}. Detrás de un proxy
 * inverso habrá que activar {@code server.forward-headers-strategy} para que sea la del cliente.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Registro, verificación de email, sesiones y recuperación de contraseña")
public class AuthController {

    private static final MensajeResponse MENSAJE_REGISTRO = new MensajeResponse(
            "Si los datos son correctos, recibirás un email para verificar tu cuenta");
    private static final MensajeResponse MENSAJE_REENVIO = new MensajeResponse(
            "Si la cuenta existe y no está verificada, recibirás un nuevo email de verificación");
    private static final MensajeResponse MENSAJE_RECUPERACION = new MensajeResponse(
            "Si la cuenta existe, recibirás un email con un enlace para restablecer la contraseña");

    private final AuthServicio authServicio;
    private final CookieRefresh cookieRefresh;

    public AuthController(AuthServicio authServicio, CookieRefresh cookieRefresh) {
        this.authServicio = authServicio;
        this.cookieRefresh = cookieRefresh;
    }

    @PostMapping("/registro")
    @SecurityRequirements
    @Operation(summary = "Crear una cuenta",
            description = "Responde siempre 202 con el mismo mensaje, exista o no el email. Máximo 3 por hora por IP.")
    public ResponseEntity<MensajeResponse> registrar(@Valid @RequestBody RegistroRequest peticion,
            HttpServletRequest http) {
        authServicio.registrar(peticion, http.getRemoteAddr());
        return ResponseEntity.accepted().body(MENSAJE_REGISTRO);
    }

    @PostMapping("/verificar-email")
    @SecurityRequirements
    @Operation(summary = "Verificar el email con el token del enlace")
    public ResponseEntity<Void> verificarEmail(@Valid @RequestBody VerificarEmailRequest peticion) {
        authServicio.verificarEmail(peticion.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reenviar-verificacion")
    @SecurityRequirements
    @Operation(summary = "Reenviar el email de verificación",
            description = "Responde siempre 202 con el mismo mensaje. Máximo 3 por hora por IP.")
    public ResponseEntity<MensajeResponse> reenviarVerificacion(
            @Valid @RequestBody ReenviarVerificacionRequest peticion, HttpServletRequest http) {
        authServicio.reenviarVerificacion(peticion.email(), http.getRemoteAddr());
        return ResponseEntity.accepted().body(MENSAJE_REENVIO);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Iniciar sesión",
            description = "Devuelve el access token y pone el refresh token en la cookie HttpOnly ns_refresh. "
                    + "Tras 5 intentos fallidos en 15 minutos responde 429 con Retry-After.")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest peticion, HttpServletRequest http) {
        return conCookie(authServicio.login(peticion, http.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Renovar el access token",
            description = "Usa la cookie ns_refresh, la rota y devuelve un access token nuevo con la cookie nueva.")
    public ResponseEntity<TokenResponse> refrescar(
            @CookieValue(name = CookieRefresh.NOMBRE, required = false) String refreshToken,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origen) {
        return conCookie(authServicio.refrescar(refreshToken, origen));
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Cerrar la sesión", description = "Usa solo la cookie ns_refresh y la borra.")
    public ResponseEntity<Void> logout(
            @CookieValue(name = CookieRefresh.NOMBRE, required = false) String refreshToken) {
        authServicio.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh.borrar().toString())
                .build();
    }

    @PostMapping("/logout-todas")
    @Operation(summary = "Cerrar todas las sesiones",
            description = "Revoca todos los refresh tokens del usuario autenticado y borra la cookie de este navegador.")
    public ResponseEntity<Void> logoutTodas() {
        authServicio.logoutTodas();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh.borrar().toString())
                .build();
    }

    @PostMapping("/recuperar-password")
    @SecurityRequirements
    @Operation(summary = "Solicitar el email de recuperación de contraseña",
            description = "Responde siempre 202 con el mismo mensaje. Máximo 3 por hora por IP.")
    public ResponseEntity<MensajeResponse> recuperarPassword(@Valid @RequestBody RecuperarPasswordRequest peticion,
            HttpServletRequest http) {
        authServicio.solicitarRecuperacion(peticion.email(), http.getRemoteAddr());
        return ResponseEntity.accepted().body(MENSAJE_RECUPERACION);
    }

    @PostMapping("/restablecer-password")
    @SecurityRequirements
    @Operation(summary = "Restablecer la contraseña con el token del enlace",
            description = "Cierra todas las sesiones abiertas del usuario.")
    public ResponseEntity<Void> restablecerPassword(@Valid @RequestBody RestablecerPasswordRequest peticion) {
        authServicio.restablecerPassword(peticion);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<TokenResponse> conCookie(ResultadoLogin resultado) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookieRefresh.crear(resultado.refreshToken(), resultado.mantenerSesion()).toString())
                .body(resultado.respuesta());
    }
}
