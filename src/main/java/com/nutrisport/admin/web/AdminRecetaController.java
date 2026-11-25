package com.nutrisport.admin.web;

import com.nutrisport.admin.dto.PublicarRecetaRequest;
import com.nutrisport.admin.dto.RecetaAdminRequest;
import com.nutrisport.admin.servicio.AdminRecetaServicio;
import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.recetas.dto.RecetaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gestión de recetas públicas por un administrador (§7.10, Ampliación). */
@RestController
@RequestMapping("/api/v1/admin/recetas")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Solo para administradores: catálogo de alimentos (alta, edición, revisión de "
        + "alérgenos e importación CSV), categorías, recetas públicas y su publicación, emparejamientos de "
        + "ingredientes y usuarios (búsqueda, bloqueo y roles). Cada acción queda auditada. Nunca da acceso a datos "
        + "de salud.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
@ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
public class AdminRecetaController {

    private final AdminRecetaServicio adminRecetaServicio;

    public AdminRecetaController(AdminRecetaServicio adminRecetaServicio) {
        this.adminRecetaServicio = adminRecetaServicio;
    }

    @PostMapping
    @Operation(summary = "Crear una receta propia pública",
            description = "Con ingredientes del catálogo (alimentos públicos activos) en gramos o con una medida. Se crea "
                    + "sin publicar y con las raciones reales, y se calculan sus nutrientes.")
    @ApiResponse(responseCode = "201", description = "Receta creada; Location apunta a su detalle")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public ResponseEntity<RecetaResponse> crear(@Valid @RequestBody RecetaAdminRequest peticion) {
        RecetaResponse creada = adminRecetaServicio.crear(peticion);
        return ResponseEntity.created(URI.create("/api/v1/recetas/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar una receta propia pública",
            description = "Sustituye sus datos y sus ingredientes y recalcula los nutrientes. 404 si no es una receta "
                    + "propia pública (las de TheMealDB y las privadas no se editan aquí).")
    @ApiResponse(responseCode = "200", description = "Receta actualizada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public RecetaResponse editar(@PathVariable UUID id, @Valid @RequestBody RecetaAdminRequest peticion) {
        return adminRecetaServicio.editar(id, peticion);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una receta propia pública",
            description = "La borra con sus ingredientes y favoritas. 404 si no es una receta propia pública.")
    @ApiResponse(responseCode = "204", description = "Receta eliminada")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        adminRecetaServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/publicacion")
    @Operation(summary = "Publicar o despublicar una receta pública",
            description = "Para recetas propias y de TheMealDB. Los campos nulos no cambian. Al fijar las raciones dejan "
                    + "de ser supuestas y se recalculan los nutrientes por ración. Solo las publicadas entran en el "
                    + "planificador.")
    @ApiResponse(responseCode = "200", description = "Receta actualizada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public RecetaResponse publicacion(@PathVariable UUID id, @Valid @RequestBody PublicarRecetaRequest peticion) {
        return adminRecetaServicio.publicacion(id, peticion);
    }
}
