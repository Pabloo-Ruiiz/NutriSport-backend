package com.nutrisport.admin.web;

import com.nutrisport.admin.dto.CategoriaRequest;
import com.nutrisport.admin.servicio.AdminCatalogoServicio;
import com.nutrisport.catalogo.dto.CategoriaResponse;
import com.nutrisport.comun.documentacion.RespuestasApi;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión de categorías de alimentos y recetas por un administrador (§7.10). El listado es el público:
 * GET /api/v1/categorias?tipo=.
 */
@RestController
@RequestMapping("/api/v1/admin/categorias")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Solo para administradores: catálogo de alimentos (alta, edición, revisión de "
        + "alérgenos e importación CSV), categorías, recetas públicas y su publicación, emparejamientos de "
        + "ingredientes y usuarios (búsqueda, bloqueo y roles). Cada acción queda auditada. Nunca da acceso a datos "
        + "de salud.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
@ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
public class AdminCategoriaController {

    private final AdminCatalogoServicio adminCatalogoServicio;

    public AdminCategoriaController(AdminCatalogoServicio adminCatalogoServicio) {
        this.adminCatalogoServicio = adminCatalogoServicio;
    }

    @PostMapping
    @Operation(summary = "Crear una categoría",
            description = "El padre, si se indica, debe existir y ser del mismo tipo. Solo las categorías de "
                    + "alimentos tienen sección de la compra. 409 si ya existe otra del mismo tipo con ese nombre.")
    @ApiResponse(responseCode = "201", description = "Categoría creada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "409", ref = RespuestasApi.CONFLICTO)
    public ResponseEntity<CategoriaResponse> crear(@Valid @RequestBody CategoriaRequest peticion) {
        CategoriaResponse creada = adminCatalogoServicio.crearCategoria(peticion);
        return ResponseEntity.created(URI.create("/api/v1/categorias?tipo=" + creada.tipo())).body(creada);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar una categoría",
            description = "Mismas reglas que al crearla; una categoría no puede quedar dentro de sí misma. 409 si el "
                    + "nombre ya existe en ese tipo o si se cambia el tipo de una categoría con elementos.")
    @ApiResponse(responseCode = "200", description = "Categoría actualizada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    @ApiResponse(responseCode = "409", ref = RespuestasApi.CONFLICTO)
    public CategoriaResponse editar(@PathVariable UUID id, @Valid @RequestBody CategoriaRequest peticion) {
        return adminCatalogoServicio.editarCategoria(id, peticion);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una categoría", description = "409 si tiene alimentos o subcategorías.")
    @ApiResponse(responseCode = "204", description = "Categoría eliminada")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    @ApiResponse(responseCode = "409", ref = RespuestasApi.CONFLICTO)
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        adminCatalogoServicio.eliminarCategoria(id);
        return ResponseEntity.noContent().build();
    }
}
