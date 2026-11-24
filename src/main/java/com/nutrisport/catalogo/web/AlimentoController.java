package com.nutrisport.catalogo.web;

import com.nutrisport.catalogo.dto.AlimentoRequest;
import com.nutrisport.catalogo.dto.AlimentoResponse;
import com.nutrisport.catalogo.dto.AlimentoResumenResponse;
import com.nutrisport.catalogo.servicio.AlimentoServicio;
import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.comun.dto.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
 * Catálogo de alimentos (§7.3). Buscar y ver el detalle: cualquier usuario autenticado (D/A). Crear,
 * editar y borrar alimentos privados propios: solo deportistas (D).
 */
@RestController
@RequestMapping("/api/v1/alimentos")
@Tag(name = "Catálogo", description = "Búsqueda de alimentos (públicos y privados propios), alimentos recientes y "
        + "frecuentes, alimentos privados del usuario y categorías. Los valores nutricionales son por 100 g o 100 ml.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
public class AlimentoController {

    private final AlimentoServicio alimentoServicio;

    public AlimentoController(AlimentoServicio alimentoServicio) {
        this.alimentoServicio = alimentoServicio;
    }

    @GetMapping
    @Operation(summary = "Buscar alimentos",
            description = "Alimentos activos públicos y privados propios. Tolera tildes, mayúsculas y errores de "
                    + "escritura; los más parecidos van primero (se ignora sort). size: máximo 50.")
    @ApiResponse(responseCode = "200", description = "Página de resultados")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public PaginaResponse<AlimentoResumenResponse> buscar(
            @Parameter(description = "Texto a buscar, de 2 a 60 caracteres (obligatorio)", example = "arroz")
            @RequestParam(required = false) String q,
            @Parameter(description = "Solo alimentos de esta categoría (GET /categorias?tipo=ALIMENTO)")
            @RequestParam(name = "categoria", required = false) UUID categoriaId,
            @ParameterObject @PageableDefault(size = 20) Pageable paginacion) {
        return alimentoServicio.buscar(q, categoriaId, paginacion);
    }

    // recientes y frecuentes se declaran antes de /{id}
    @GetMapping("/recientes")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Alimentos usados recientemente",
            description = "Los 20 últimos alimentos distintos registrados en tus comidas, el más reciente primero.")
    @ApiResponse(responseCode = "200", description = "Alimentos recientes")
    @ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
    public List<AlimentoResumenResponse> recientes() {
        return alimentoServicio.recientes();
    }

    @GetMapping("/frecuentes")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Alimentos más usados",
            description = "Los 20 alimentos que más has registrado en los últimos 90 días.")
    @ApiResponse(responseCode = "200", description = "Alimentos frecuentes")
    @ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
    public List<AlimentoResumenResponse> frecuentes() {
        return alimentoServicio.frecuentes();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un alimento",
            description = "Valores nutricionales, porciones caseras, alérgenos y aptitud para dietas. 404 si no "
                    + "existe o es privado de otro usuario.")
    @ApiResponse(responseCode = "200", description = "El alimento")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public AlimentoResponse obtener(@PathVariable UUID id) {
        return alimentoServicio.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Crear un alimento privado",
            description = "Solo lo ve su creador; queda sin validar y con los alérgenos sin revisar. Vegano implica "
                    + "vegetariano y vegetariano implica pescetariano.")
    @ApiResponse(responseCode = "201", description = "Alimento creado; Location apunta a él")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
    public ResponseEntity<AlimentoResponse> crear(@Valid @RequestBody AlimentoRequest peticion) {
        AlimentoResponse creado = alimentoServicio.crearPrivado(peticion);
        return ResponseEntity.created(URI.create("/api/v1/alimentos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Editar un alimento privado propio",
            description = "Mismas reglas que al crearlo. 404 si no es un alimento privado propio y activo.")
    @ApiResponse(responseCode = "200", description = "Alimento actualizado")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public AlimentoResponse editar(@PathVariable UUID id, @Valid @RequestBody AlimentoRequest peticion) {
        return alimentoServicio.editar(id, peticion);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Eliminar un alimento privado propio",
            description = "Si ya se ha usado en alguna comida solo se desactiva (el historial lo conserva). 404 si "
                    + "no es un alimento privado propio y activo.")
    @ApiResponse(responseCode = "204", description = "Alimento eliminado o desactivado")
    @ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        alimentoServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
