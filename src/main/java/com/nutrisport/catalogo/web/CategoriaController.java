package com.nutrisport.catalogo.web;

import com.nutrisport.catalogo.dto.CategoriaResponse;
import com.nutrisport.catalogo.entidad.TipoCategoria;
import com.nutrisport.catalogo.servicio.CategoriaServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Categorías del catálogo (§7.3), para cualquier usuario autenticado. */
@RestController
@RequestMapping("/api/v1/categorias")
@Tag(name = "Categorías", description = "Categorías de alimentos y recetas")
public class CategoriaController {

    private final CategoriaServicio categoriaServicio;

    public CategoriaController(CategoriaServicio categoriaServicio) {
        this.categoriaServicio = categoriaServicio;
    }

    @GetMapping
    @Operation(summary = "Listar categorías de un tipo", description = "tipo = ALIMENTO o RECETA, en su orden.")
    public List<CategoriaResponse> listar(@RequestParam TipoCategoria tipo) {
        return categoriaServicio.listar(tipo);
    }
}
