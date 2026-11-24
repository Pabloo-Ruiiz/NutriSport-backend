package com.nutrisport.actividad.web;

import com.nutrisport.actividad.dto.EntrenamientoRequest;
import com.nutrisport.actividad.dto.EntrenamientoResponse;
import com.nutrisport.actividad.dto.TipoActividadResponse;
import com.nutrisport.actividad.entidad.EstadoEntrenamiento;
import com.nutrisport.actividad.servicio.EntrenamientoServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entrenamientos del deportista autenticado (§7.8). El catálogo de tipos es para cualquier usuario
 * autenticado (D/A); el resto, solo para deportistas. Son datos de salud: con el consentimiento revocado
 * responden 403 CONSENTIMIENTO_REVOCADO (WebConfig).
 */
@RestController
@RequestMapping("/api/v1/entrenamientos")
@Tag(name = "Entrenamientos", description = "Registro y planificación de entrenamientos con su gasto estimado")
public class EntrenamientoController {

    private final EntrenamientoServicio entrenamientoServicio;

    public EntrenamientoController(EntrenamientoServicio entrenamientoServicio) {
        this.entrenamientoServicio = entrenamientoServicio;
    }

    @GetMapping
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Historial y próximos entrenamientos",
            description = "Entre desde y hasta (ambos incluidos, máximo 92 días), opcionalmente de un estado.")
    public List<EntrenamientoResponse> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoEntrenamiento estado) {
        return entrenamientoServicio.listar(desde, hasta, estado);
    }

    @GetMapping("/tipos")
    @Operation(summary = "Catálogo de actividades con su MET")
    public List<TipoActividadResponse> tipos() {
        return entrenamientoServicio.tipos();
    }

    @PostMapping
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Registrar o planificar un entrenamiento",
            description = "Sin estado: PLANIFICADO si la fecha es futura y REALIZADO si no. Calcula el gasto neto "
                    + "estimado y recalcula el objetivo del día. 422 PERFIL_REQUERIDO si no hay perfil.")
    public ResponseEntity<EntrenamientoResponse> crear(@Valid @RequestBody EntrenamientoRequest peticion) {
        EntrenamientoResponse creado = entrenamientoServicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/entrenamientos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Editar un entrenamiento (incluido marcarlo como realizado)",
            description = "Sin estado se conserva el actual. Recalcula el gasto y los objetivos de los días afectados.")
    public EntrenamientoResponse editar(@PathVariable UUID id, @Valid @RequestBody EntrenamientoRequest peticion) {
        return entrenamientoServicio.editar(id, peticion);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Eliminar un entrenamiento", description = "Recalcula el objetivo de su día.")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        entrenamientoServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
