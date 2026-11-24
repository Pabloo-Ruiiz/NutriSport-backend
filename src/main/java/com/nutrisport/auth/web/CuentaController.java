package com.nutrisport.auth.web;

import com.nutrisport.auth.dto.ActualizarCuentaRequest;
import com.nutrisport.auth.dto.CambiarEmailRequest;
import com.nutrisport.auth.dto.CambiarPasswordRequest;
import com.nutrisport.auth.dto.CuentaResponse;
import com.nutrisport.auth.dto.EliminarCuentaRequest;
import com.nutrisport.auth.servicio.CuentaServicio;
import com.nutrisport.auth.servicio.EliminacionCuentaServicio;
import com.nutrisport.comun.dto.MensajeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@Tag(name = "Cuenta", description = "Datos de la cuenta, contraseña, email, consentimiento y eliminación")
public class CuentaController {

    private static final MensajeResponse MENSAJE_CAMBIO_EMAIL = new MensajeResponse(
            "Si el email está disponible, recibirás en él un enlace para confirmar el cambio");

    private final CuentaServicio cuentaServicio;
    private final EliminacionCuentaServicio eliminacionCuentaServicio;
    private final CookieRefresh cookieRefresh;

    public CuentaController(CuentaServicio cuentaServicio, EliminacionCuentaServicio eliminacionCuentaServicio,
            CookieRefresh cookieRefresh) {
        this.cuentaServicio = cuentaServicio;
        this.eliminacionCuentaServicio = eliminacionCuentaServicio;
        this.cookieRefresh = cookieRefresh;
    }

    @GetMapping
    @Operation(summary = "Datos de la cuenta")
    public CuentaResponse obtener() {
        return cuentaServicio.obtener();
    }

    @PatchMapping
    @Operation(summary = "Actualizar nombre y unidades", description = "Los campos que no se envían no cambian.")
    public CuentaResponse actualizar(@Valid @RequestBody ActualizarCuentaRequest peticion) {
        return cuentaServicio.actualizar(peticion);
    }

    @PutMapping("/password")
    @Operation(summary = "Cambiar la contraseña", description = "Requiere la actual y cierra todas las sesiones.")
    public ResponseEntity<Void> cambiarPassword(@Valid @RequestBody CambiarPasswordRequest peticion) {
        cuentaServicio.cambiarPassword(peticion);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/email")
    @Operation(summary = "Cambiar el email",
            description = "Envía un enlace de confirmación al email nuevo. Responde siempre 202 con el mismo mensaje.")
    public ResponseEntity<MensajeResponse> cambiarEmail(@Valid @RequestBody CambiarEmailRequest peticion) {
        cuentaServicio.solicitarCambioEmail(peticion);
        return ResponseEntity.accepted().body(MENSAJE_CAMBIO_EMAIL);
    }

    @DeleteMapping("/consentimiento-salud")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Revocar el consentimiento de datos de salud",
            description = "Bloquea perfil, comidas, análisis, entrenamientos y seguimiento (403 CONSENTIMIENTO_REVOCADO).")
    public ResponseEntity<Void> revocarConsentimiento() {
        cuentaServicio.revocarConsentimiento();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/consentimiento-salud")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Volver a dar el consentimiento de datos de salud (versión vigente del texto)")
    public ResponseEntity<Void> darConsentimiento() {
        cuentaServicio.darConsentimiento();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Eliminar la cuenta",
            description = "Requiere la contraseña. Inmediata o diferida 30 días. Cierra todas las sesiones.")
    public ResponseEntity<Void> eliminar(@Valid @RequestBody EliminarCuentaRequest peticion) {
        eliminacionCuentaServicio.eliminar(peticion);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieRefresh.borrar().toString())
                .build();
    }
}
