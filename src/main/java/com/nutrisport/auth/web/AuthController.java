package com.nutrisport.auth.web;

import com.nutrisport.auth.dto.LoginRequest;
import com.nutrisport.auth.dto.ReenviarVerificacionRequest;
import com.nutrisport.auth.dto.RegistroRequest;
import com.nutrisport.auth.dto.TokenResponse;
import com.nutrisport.auth.dto.VerificarEmailRequest;
import com.nutrisport.auth.servicio.AuthServicio;
import com.nutrisport.auth.servicio.ResultadoLogin;
import com.nutrisport.comun.dto.MensajeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Autenticación pública (§7.1). {@code @SecurityRequirements} vacío: estas operaciones no piden JWT. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Registro, verificación de email e inicio de sesión")
public class AuthController {

    private static final MensajeResponse MENSAJE_REGISTRO = new MensajeResponse(
            "Si los datos son correctos, recibirás un email para verificar tu cuenta");
    private static final MensajeResponse MENSAJE_REENVIO = new MensajeResponse(
            "Si la cuenta existe y no está verificada, recibirás un nuevo email de verificación");

    private final AuthServicio authServicio;
    private final CookieRefresh cookieRefresh;

    public AuthController(AuthServicio authServicio, CookieRefresh cookieRefresh) {
        this.authServicio = authServicio;
        this.cookieRefresh = cookieRefresh;
    }

    @PostMapping("/registro")
    @SecurityRequirements
    @Operation(summary = "Crear una cuenta",
            description = "Responde siempre 202 con el mismo mensaje, exista o no el email.")
    public ResponseEntity<MensajeResponse> registrar(@Valid @RequestBody RegistroRequest peticion) {
        authServicio.registrar(peticion);
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
            description = "Responde siempre 202 con el mismo mensaje.")
    public ResponseEntity<MensajeResponse> reenviarVerificacion(
            @Valid @RequestBody ReenviarVerificacionRequest peticion) {
        authServicio.reenviarVerificacion(peticion.email());
        return ResponseEntity.accepted().body(MENSAJE_REENVIO);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Iniciar sesión",
            description = "Devuelve el access token y pone el refresh token en la cookie HttpOnly ns_refresh.")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest peticion) {
        ResultadoLogin resultado = authServicio.login(peticion);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookieRefresh.crear(resultado.refreshToken(), resultado.mantenerSesion()).toString())
                .body(resultado.respuesta());
    }
}
