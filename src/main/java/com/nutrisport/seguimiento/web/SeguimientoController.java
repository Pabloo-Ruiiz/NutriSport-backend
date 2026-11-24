package com.nutrisport.seguimiento.web;

import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.seguimiento.dto.PesoRequest;
import com.nutrisport.seguimiento.dto.PesoResponse;
import com.nutrisport.seguimiento.dto.ResumenDiarioResponse;
import com.nutrisport.seguimiento.dto.SeriePesoResponse;
import com.nutrisport.seguimiento.servicio.PesoServicio;
import com.nutrisport.seguimiento.servicio.ResumenDiarioServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seguimiento del deportista autenticado (§7.7): resumen diario y evolución del peso. Son datos de
 * salud: con el consentimiento revocado responden 403 CONSENTIMIENTO_REVOCADO (WebConfig).
 */
@RestController
@RequestMapping("/api/v1/seguimiento")
@PreAuthorize("hasRole('DEPORTISTA')")
@Tag(name = "Seguimiento", description = "Resumen diario (consumido frente al objetivo del día, con comidas y "
        + "entrenamientos) y evolución del peso con su media móvil.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
@ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
public class SeguimientoController {

    private final ResumenDiarioServicio resumenDiarioServicio;
    private final PesoServicio pesoServicio;

    public SeguimientoController(ResumenDiarioServicio resumenDiarioServicio, PesoServicio pesoServicio) {
        this.resumenDiarioServicio = resumenDiarioServicio;
        this.pesoServicio = pesoServicio;
    }

    @GetMapping("/resumen")
    @Operation(summary = "Resumen diario",
            description = "Objetivo del día (base del perfil más el gasto de los entrenamientos que cuentan), "
                    + "consumido, restante, exceso, porcentajes, comidas y entrenamientos. 422 PERFIL_REQUERIDO si "
                    + "no hay perfil.")
    @ApiResponse(responseCode = "200", description = "Resumen del día")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "422", ref = RespuestasApi.REGLA_NEGOCIO)
    public ResumenDiarioResponse resumen(
            @Parameter(description = "Día del resumen; sin él, hoy (zona Europe/Madrid)", example = "2026-11-24")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return resumenDiarioServicio.de(fecha);
    }

    @GetMapping("/peso")
    @Operation(summary = "Serie de peso con media móvil de 7 días",
            description = "Pesajes entre desde y hasta (ambos incluidos, máximo 366 días) en orden cronológico.")
    @ApiResponse(responseCode = "200", description = "Serie de pesajes")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public SeriePesoResponse seriePeso(
            @Parameter(description = "Primer día (incluido)", example = "2026-09-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Último día (incluido); como mucho 366 días después de desde", example = "2026-11-24")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return pesoServicio.serie(desde, hasta);
    }

    @PostMapping("/peso")
    @Operation(summary = "Registrar un pesaje",
            description = "Peso de 30 a 250 kg. Si ya hay uno ese día, lo sustituye. Si es el más reciente, "
                    + "actualiza el peso del perfil y recalcula sus objetivos.")
    @ApiResponse(responseCode = "201", description = "Pesaje registrado; Location apunta a él")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public ResponseEntity<PesoResponse> registrarPeso(@Valid @RequestBody PesoRequest peticion) {
        PesoResponse pesaje = pesoServicio.registrar(peticion);
        return ResponseEntity.created(URI.create("/api/v1/seguimiento/peso/" + pesaje.id())).body(pesaje);
    }

    @DeleteMapping("/peso/{id}")
    @Operation(summary = "Eliminar un pesaje", description = "Si era el más reciente, el perfil toma el anterior.")
    @ApiResponse(responseCode = "204", description = "Pesaje eliminado")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ResponseEntity<Void> eliminarPeso(@PathVariable UUID id) {
        pesoServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
