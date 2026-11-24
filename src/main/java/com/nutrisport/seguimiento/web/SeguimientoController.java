package com.nutrisport.seguimiento.web;

import com.nutrisport.seguimiento.dto.PesoRequest;
import com.nutrisport.seguimiento.dto.PesoResponse;
import com.nutrisport.seguimiento.dto.ResumenDiarioResponse;
import com.nutrisport.seguimiento.dto.SeriePesoResponse;
import com.nutrisport.seguimiento.servicio.PesoServicio;
import com.nutrisport.seguimiento.servicio.ResumenDiarioServicio;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Seguimiento", description = "Resumen diario y evolución del peso")
public class SeguimientoController {

    private final ResumenDiarioServicio resumenDiarioServicio;
    private final PesoServicio pesoServicio;

    public SeguimientoController(ResumenDiarioServicio resumenDiarioServicio, PesoServicio pesoServicio) {
        this.resumenDiarioServicio = resumenDiarioServicio;
        this.pesoServicio = pesoServicio;
    }

    @GetMapping("/resumen")
    @Operation(summary = "Resumen diario",
            description = "Consumido frente al objetivo del día, con comidas y entrenamientos. Sin fecha, hoy. "
                    + "422 PERFIL_REQUERIDO si no hay perfil.")
    public ResumenDiarioResponse resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return resumenDiarioServicio.de(fecha);
    }

    @GetMapping("/peso")
    @Operation(summary = "Serie de peso con media móvil de 7 días",
            description = "Pesajes entre desde y hasta (ambos incluidos, máximo 366 días).")
    public SeriePesoResponse seriePeso(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return pesoServicio.serie(desde, hasta);
    }

    @PostMapping("/peso")
    @Operation(summary = "Registrar un pesaje",
            description = "Si ya hay uno ese día, lo sustituye. Si es el más reciente, actualiza el peso del perfil "
                    + "y recalcula sus objetivos.")
    public ResponseEntity<PesoResponse> registrarPeso(@Valid @RequestBody PesoRequest peticion) {
        PesoResponse pesaje = pesoServicio.registrar(peticion);
        return ResponseEntity.created(URI.create("/api/v1/seguimiento/peso/" + pesaje.id())).body(pesaje);
    }

    @DeleteMapping("/peso/{id}")
    @Operation(summary = "Eliminar un pesaje", description = "Si era el más reciente, el perfil toma el anterior.")
    public ResponseEntity<Void> eliminarPeso(@PathVariable UUID id) {
        pesoServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
