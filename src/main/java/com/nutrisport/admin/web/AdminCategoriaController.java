package com.nutrisport.admin.web;

import com.nutrisport.admin.dto.CategoriaRequest;
import com.nutrisport.admin.servicio.AdminCatalogoServicio;
import com.nutrisport.catalogo.dto.CategoriaResponse;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Administración: categorías", description = "Alta, edición y borrado de categorías")
public class AdminCategoriaController {

    private final AdminCatalogoServicio adminCatalogoServicio;

    public AdminCategoriaController(AdminCatalogoServicio adminCatalogoServicio) {
        this.adminCatalogoServicio = adminCatalogoServicio;
    }

    @PostMapping
    @Operation(summary = "Crear una categoría", description = "409 si ya existe otra del mismo tipo con ese nombre.")
    public ResponseEntity<CategoriaResponse> crear(@Valid @RequestBody CategoriaRequest peticion) {
        CategoriaResponse creada = adminCatalogoServicio.crearCategoria(peticion);
        return ResponseEntity.created(URI.create("/api/v1/categorias?tipo=" + creada.tipo())).body(creada);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar una categoría", description = "409 si el nombre ya existe en ese tipo.")
    public CategoriaResponse editar(@PathVariable UUID id, @Valid @RequestBody CategoriaRequest peticion) {
        return adminCatalogoServicio.editarCategoria(id, peticion);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una categoría", description = "409 si tiene alimentos o subcategorías.")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        adminCatalogoServicio.eliminarCategoria(id);
        return ResponseEntity.noContent().build();
    }
}
