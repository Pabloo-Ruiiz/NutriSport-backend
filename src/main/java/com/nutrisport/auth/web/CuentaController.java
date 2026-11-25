package com.nutrisport.auth.web;

import com.nutrisport.auth.dto.ActualizarCuentaRequest;
import com.nutrisport.auth.dto.CambiarEmailRequest;
import com.nutrisport.auth.dto.CambiarPasswordRequest;
import com.nutrisport.auth.dto.CuentaResponse;
import com.nutrisport.auth.dto.EliminarCuentaRequest;
import com.nutrisport.auth.dto.ExportacionDatos;
import com.nutrisport.auth.servicio.CuentaServicio;
import com.nutrisport.auth.servicio.EliminacionCuentaServicio;
import com.nutrisport.auth.servicio.ExportacionServicio;
import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.comun.dto.MensajeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cuenta del usuario autenticado (§7.1). El consentimiento de salud y la eliminación de la cuenta
 * son solo para deportistas (D en §7.1); el resto, para cualquier usuario autenticado (D/A).
 */
@RestController
@RequestMapping("/api/v1/cuenta")
@Tag(name = "Cuenta", description = "Datos de la cuenta del usuario autenticado: nombre y unidades, contraseña, "
        + "email, consentimiento de tratamiento de datos de salud (RGPD art. 9) y eliminación de la cuenta.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
public class CuentaController {

    private static final MensajeResponse MENSAJE_CAMBIO_EMAIL = new MensajeResponse(
            "Si el email está disponible, recibirás en él un enlace para confirmar el cambio");

    private final CuentaServicio cuentaServicio;
    private final EliminacionCuentaServicio eliminacionCuentaServicio;
    private final CookieRefresh cookieRefresh;
    private final ExportacionServicio exportacionServicio;
    private final Clock reloj;

    public CuentaController(CuentaServicio cuentaServicio, EliminacionCuentaServicio eliminacionCuentaServicio,
            CookieRefresh cookieRefresh, ExportacionServicio exportacionServicio, Clock reloj) {
        this.cuentaServicio = cuentaServicio;
        this.eliminacionCuentaServicio = eliminacionCuentaServicio;
        this.cookieRefresh = cookieRefresh;
        this.exportacionServicio = exportacionServicio;
        this.reloj = reloj;
    }

    @GetMapping
    @Operation(summary = "Datos de la cuenta",
            description = "Nombre, email, roles, estado, si el email está verificado, si el consentimiento de salud "
                    + "está vigente y las unidades.")
    @ApiResponse(responseCode = "200", description = "La cuenta")
    public CuentaResponse obtener() {
        return cuentaServicio.obtener();
    }

    @PatchMapping
    @Operation(summary = "Actualizar nombre y unidades", description = "Los campos que no se envían no cambian.")
    @ApiResponse(responseCode = "200", description = "Cuenta actualizada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public CuentaResponse actualizar(@Valid @RequestBody ActualizarCuentaRequest peticion) {
        return cuentaServicio.actualizar(peticion);
    }

    @PutMapping("/password")
    @Operation(summary = "Cambiar la contraseña",
            description = "Requiere la contraseña actual (400 PASSWORD_INCORRECTA si no coincide). La nueva sigue las "
                    + "reglas del registro. Cierra todas las sesiones.")
    @ApiResponse(responseCode = "204", description = "Contraseña cambiada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public ResponseEntity<Void> cambiarPassword(@Valid @RequestBody CambiarPasswordRequest peticion) {
        cuentaServicio.cambiarPassword(peticion);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/email")
    @Operation(summary = "Cambiar el email",
            description = "Requiere la contraseña. Envía un enlace de confirmación al email nuevo; el cambio se hace "
                    + "al abrirlo (POST /auth/verificar-email). Responde siempre 202 con el mismo mensaje, para no "
                    + "revelar si el email ya está en uso.")
    @ApiResponse(responseCode = "202", description = "Petición aceptada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public ResponseEntity<MensajeResponse> cambiarEmail(@Valid @RequestBody CambiarEmailRequest peticion) {
        cuentaServicio.solicitarCambioEmail(peticion);
        return ResponseEntity.accepted().body(MENSAJE_CAMBIO_EMAIL);
    }

    @GetMapping("/exportacion")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Exportar todos mis datos",
            description = "Derecho de acceso y portabilidad (§8.3): un JSON descargable "
                    + "(nutrisport-datos-AAAAMMDD.json) con la cuenta y sus consentimientos, el perfil, las "
                    + "preferencias, los objetivos diarios, las comidas con sus alimentos, los entrenamientos, los "
                    + "pesajes, los menús, las listas de la compra, las recetas favoritas, las conversaciones con el "
                    + "asistente, los análisis de fotos (metadatos y candidatos, sin imágenes) y los insights. Nunca "
                    + "incluye la contraseña ni los tokens. Se puede usar aunque el consentimiento de salud esté "
                    + "revocado. 403 EMAIL_NO_VERIFICADO si el email no está verificado.")
    @ApiResponse(responseCode = "200", description = "Fichero JSON con todos los datos")
    @ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
    public ResponseEntity<ExportacionDatos> exportar() {
        ExportacionDatos datos = exportacionServicio.exportar();
        String fichero = "nutrisport-datos-" + LocalDate.now(reloj).format(DateTimeFormatter.BASIC_ISO_DATE)
                + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fichero).build().toString())
                .body(datos);
    }

    @DeleteMapping("/consentimiento-salud")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Revocar el consentimiento de datos de salud",
            description = "Bloquea perfil, comidas, análisis, entrenamientos y seguimiento (403 "
                    + "CONSENTIMIENTO_REVOCADO) hasta que se vuelva a dar. Los datos no se borran: se pueden "
                    + "eliminar con DELETE /cuenta.")
    @ApiResponse(responseCode = "204", description = "Consentimiento revocado")
    @ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
    public ResponseEntity<Void> revocarConsentimiento() {
        cuentaServicio.revocarConsentimiento();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/consentimiento-salud")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Volver a dar el consentimiento de datos de salud",
            description = "Guarda la fecha y la versión vigente del texto del consentimiento y desbloquea las "
                    + "funciones con datos de salud.")
    @ApiResponse(responseCode = "204", description = "Consentimiento registrado")
    @ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
    public ResponseEntity<Void> darConsentimiento() {
        cuentaServicio.darConsentimiento();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Eliminar la cuenta",
            description = "Requiere la contraseña (400 PASSWORD_INCORRECTA si no coincide). Con inmediata = true se "
                    + "borra todo al momento; si no, la cuenta queda ELIMINADA y se borra a los 30 días. En ambos "
                    + "casos se borran las fotos, se cierran todas las sesiones y se borra la cookie.")
    @ApiResponse(responseCode = "204", description = "Cuenta eliminada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
    @ApiResponse(responseCode = "503", ref = RespuestasApi.SERVICIO_NO_DISPONIBLE)
    public ResponseEntity<Void> eliminar(@Valid @RequestBody EliminarCuentaRequest peticion) {
        eliminacionCuentaServicio.eliminar(peticion);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh.borrar().toString())
                .build();
    }
}
