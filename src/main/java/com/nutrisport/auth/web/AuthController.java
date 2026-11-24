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
import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.comun.dto.MensajeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autenticación (§7.1). Todas las operaciones son públicas salvo {@code /logout-todas}, la única que lleva
 * {@code @SecurityRequirement}.
 * <p>
 * La IP para el límite de intentos es {@link HttpServletRequest#getRemoteAddr()}; detrás del proxy la
 * calcula {@code server.forward-headers-strategy} a partir de {@code X-Forwarded-For}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registro, verificación del email, inicio y cierre de sesión, renovación del "
        + "access token y recuperación de la contraseña. El refresh token viaja solo en la cookie HttpOnly "
        + "ns_refresh (Path=/api/v1/auth, SameSite=Strict).")
public class AuthController {

    private static final MensajeResponse MENSAJE_REGISTRO = new MensajeResponse(
            "Si los datos son correctos, recibirás un email para verificar tu cuenta");
    private static final MensajeResponse MENSAJE_REENVIO = new MensajeResponse(
            "Si la cuenta existe y no está verificada, recibirás un nuevo email de verificación");
    private static final MensajeResponse MENSAJE_RECUPERACION = new MensajeResponse(
            "Si la cuenta existe, recibirás un email con un enlace para restablecer la contraseña");

    private static final String EJEMPLO_REGISTRO = """
            {
              "nombre": "Álex",
              "email": "alex@ejemplo.com",
              "password": "MediaMaraton2026",
              "mayorDeEdad": true,
              "aceptaTerminos": true,
              "consentimientoSalud": true
            }""";

    private static final String EJEMPLO_LOGIN = """
            {
              "email": "alex@ejemplo.com",
              "password": "MediaMaraton2026",
              "mantenerSesion": true
            }""";

    private static final String COOKIE_REFRESH = "Cookie ns_refresh con el refresh token nuevo (HttpOnly, Secure, "
            + "SameSite=Strict). Dura 7 días con mantenerSesion; si no, es una cookie de sesión.";

    private final AuthServicio authServicio;
    private final CookieRefresh cookieRefresh;

    public AuthController(AuthServicio authServicio, CookieRefresh cookieRefresh) {
        this.authServicio = authServicio;
        this.cookieRefresh = cookieRefresh;
    }

    @PostMapping("/registro")
    @Operation(summary = "Crear una cuenta",
            description = "Contraseña de 10 a 72 caracteres con letras y números, que no sea común. Hay que "
                    + "declarar la mayoría de edad y aceptar los términos y el tratamiento de datos de salud. "
                    + "Responde siempre 202 con el mismo mensaje, exista o no el email (si existe, se avisa a su "
                    + "titular por correo). Máximo 3 por hora por IP.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = RegistroRequest.class),
                    examples = @ExampleObject(name = "Registro", summary = "Alta de un deportista",
                            value = EJEMPLO_REGISTRO))))
    @ApiResponse(responseCode = "202", description = "Petición aceptada; si procede, se envía el email de verificación")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "429", ref = RespuestasApi.LIMITE_EXCEDIDO)
    public ResponseEntity<MensajeResponse> registrar(@Valid @RequestBody RegistroRequest peticion,
            HttpServletRequest http) {
        authServicio.registrar(peticion, http.getRemoteAddr());
        return ResponseEntity.accepted().body(MENSAJE_REGISTRO);
    }

    @PostMapping("/verificar-email")
    @Operation(summary = "Verificar el email con el token del enlace",
            description = "Consume el token del correo de verificación (24 h) o de cambio de email (en ese caso "
                    + "sustituye el email por el nuevo). Cada token sirve una sola vez.")
    @ApiResponse(responseCode = "204", description = "Email verificado")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "409", ref = RespuestasApi.CONFLICTO)
    public ResponseEntity<Void> verificarEmail(@Valid @RequestBody VerificarEmailRequest peticion) {
        authServicio.verificarEmail(peticion.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reenviar-verificacion")
    @Operation(summary = "Reenviar el email de verificación",
            description = "Solo se envía si la cuenta existe, está activa y no está verificada, pero responde "
                    + "siempre 202 con el mismo mensaje. Máximo 3 por hora por IP.")
    @ApiResponse(responseCode = "202", description = "Petición aceptada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "429", ref = RespuestasApi.LIMITE_EXCEDIDO)
    public ResponseEntity<MensajeResponse> reenviarVerificacion(
            @Valid @RequestBody ReenviarVerificacionRequest peticion, HttpServletRequest http) {
        authServicio.reenviarVerificacion(peticion.email(), http.getRemoteAddr());
        return ResponseEntity.accepted().body(MENSAJE_REENVIO);
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión",
            description = "Devuelve el access token (15 minutos) y pone el refresh token en la cookie HttpOnly "
                    + "ns_refresh. Se puede entrar sin verificar el email. El error es el mismo para credenciales "
                    + "incorrectas y cuentas bloqueadas o eliminadas. Tras 5 intentos fallidos en 15 minutos con el "
                    + "mismo email o desde la misma IP responde 429 con Retry-After.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = LoginRequest.class),
                    examples = @ExampleObject(name = "Login", summary = "Inicio de sesión manteniendo la sesión",
                            value = EJEMPLO_LOGIN))))
    @ApiResponse(responseCode = "200", description = "Sesión iniciada",
            headers = @Header(name = HttpHeaders.SET_COOKIE, description = COOKIE_REFRESH))
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
    @ApiResponse(responseCode = "429", ref = RespuestasApi.LIMITE_EXCEDIDO)
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest peticion, HttpServletRequest http) {
        return conCookie(authServicio.login(peticion, http.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar el access token",
            description = "Usa la cookie ns_refresh, la rota (el token anterior deja de valer) y devuelve un access "
                    + "token nuevo con la cookie nueva. Reutilizar un token ya rotado cierra todas las sesiones de "
                    + "su familia. Si llega la cabecera Origin, debe ser la del frontend.")
    @ApiResponse(responseCode = "200", description = "Access token renovado",
            headers = @Header(name = HttpHeaders.SET_COOKIE, description = COOKIE_REFRESH))
    @ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
    @ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
    public ResponseEntity<TokenResponse> refrescar(
            @Parameter(in = ParameterIn.COOKIE, description = "Refresh token (lo envía el navegador)")
            @CookieValue(name = CookieRefresh.NOMBRE, required = false) String refreshToken,
            @Parameter(description = "Origen de la petición (lo pone el navegador)", example = "http://localhost:4200")
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origen) {
        return conCookie(authServicio.refrescar(refreshToken, origen));
    }

    @PostMapping("/logout")
    @Operation(summary = "Cerrar la sesión",
            description = "Usa solo la cookie ns_refresh: revoca su sesión y borra la cookie. Sin cookie o con un "
                    + "token desconocido también responde 204.")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada y cookie borrada")
    public ResponseEntity<Void> logout(
            @Parameter(in = ParameterIn.COOKIE, description = "Refresh token (lo envía el navegador)")
            @CookieValue(name = CookieRefresh.NOMBRE, required = false) String refreshToken) {
        authServicio.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh.borrar().toString())
                .build();
    }

    @PostMapping("/logout-todas")
    @SecurityRequirement(name = RespuestasApi.SEGURIDAD)
    @Operation(summary = "Cerrar todas las sesiones",
            description = "Revoca todos los refresh tokens del usuario autenticado (en todos sus dispositivos) y "
                    + "borra la cookie de este navegador. Los access tokens emitidos caducan en 15 minutos.")
    @ApiResponse(responseCode = "204", description = "Sesiones cerradas")
    @ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
    public ResponseEntity<Void> logoutTodas() {
        authServicio.logoutTodas();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh.borrar().toString())
                .build();
    }

    @PostMapping("/recuperar-password")
    @Operation(summary = "Solicitar el email de recuperación de contraseña",
            description = "Si la cuenta existe y está activa, envía un enlace válido 30 minutos. Responde siempre "
                    + "202 con el mismo mensaje. Máximo 3 por hora por IP.")
    @ApiResponse(responseCode = "202", description = "Petición aceptada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "429", ref = RespuestasApi.LIMITE_EXCEDIDO)
    public ResponseEntity<MensajeResponse> recuperarPassword(@Valid @RequestBody RecuperarPasswordRequest peticion,
            HttpServletRequest http) {
        authServicio.solicitarRecuperacion(peticion.email(), http.getRemoteAddr());
        return ResponseEntity.accepted().body(MENSAJE_RECUPERACION);
    }

    @PostMapping("/restablecer-password")
    @Operation(summary = "Restablecer la contraseña con el token del enlace",
            description = "La contraseña nueva sigue las mismas reglas que en el registro. El token sirve una sola "
                    + "vez. Cierra todas las sesiones abiertas del usuario.")
    @ApiResponse(responseCode = "204", description = "Contraseña cambiada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
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
