package com.nutrisport.catalogo.web;

import com.nutrisport.catalogo.dto.AlimentoRequest;
import com.nutrisport.catalogo.dto.AlimentoResponse;
import com.nutrisport.catalogo.dto.AlimentoResumenResponse;
import com.nutrisport.catalogo.servicio.AlimentoServicio;
import com.nutrisport.comun.dto.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Alimentos", description = "Búsqueda y alimentos privados del usuario")
public class AlimentoController {

    private final AlimentoServicio alimentoServicio;

    public AlimentoController(AlimentoServicio alimentoServicio) {
        this.alimentoServicio = alimentoServicio;
    }

    @GetMapping
    @Operation(summary = "Buscar alimentos",
            description = "Alimentos activos públicos y privados propios. Tolera tildes, mayúsculas y errores de "
                    + "escritura; los más parecidos van primero. q: 2–60 caracteres; size: máximo 50.")
    public PaginaResponse<AlimentoResumenResponse> buscar(@RequestParam(required = false) String q,
            @RequestParam(name = "categoria", required = false) UUID categoriaId,
            @ParameterObject @PageableDefault(size = 20) Pageable paginacion) {
        return alimentoServicio.buscar(q, categoriaId, paginacion);
    }

    // recientes y frecuentes se declaran antes de /{id}
    @GetMapping("/recientes")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Alimentos usados recientemente",
            description = "Los 20 últimos alimentos distintos registrados en tus comidas, el más reciente primero.")
    public List<AlimentoResumenResponse> recientes() {
        return alimentoServicio.recientes();
    }

    @GetMapping("/frecuentes")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Alimentos más usados",
            description = "Los 20 alimentos que más has registrado en los últimos 90 días.")
    public List<AlimentoResumenResponse> frecuentes() {
        return alimentoServicio.frecuentes();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un alimento", description = "404 si no existe o es privado de otro usuario.")
    public AlimentoResponse obtener(@PathVariable UUID id) {
        return alimentoServicio.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Crear un alimento privado", description = "Solo lo ve su creador; queda sin validar.")
    public ResponseEntity<AlimentoResponse> crear(@Valid @RequestBody AlimentoRequest peticion) {
        AlimentoResponse creado = alimentoServicio.crearPrivado(peticion);
        return ResponseEntity.created(URI.create("/api/v1/alimentos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Editar un alimento privado propio", description = "404 si no es un alimento privado propio.")
    public AlimentoResponse editar(@PathVariable UUID id, @Valid @RequestBody AlimentoRequest peticion) {
        return alimentoServicio.editar(id, peticion);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Eliminar un alimento privado propio",
            description = "Si ya se ha usado en alguna comida solo se desactiva. 404 si no es un alimento privado propio.")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        alimentoServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
