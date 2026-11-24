package com.nutrisport.catalogo.web;

import com.nutrisport.catalogo.dto.CategoriaResponse;
import com.nutrisport.catalogo.entidad.TipoCategoria;
import com.nutrisport.catalogo.servicio.CategoriaServicio;
import com.nutrisport.comun.documentacion.RespuestasApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Categorías del catálogo (§7.3), para cualquier usuario autenticado. */
@RestController
@RequestMapping("/api/v1/categorias")
@Tag(name = "Catálogo", description = "Búsqueda de alimentos (públicos y privados propios), alimentos recientes y "
        + "frecuentes, alimentos privados del usuario y categorías. Los valores nutricionales son por 100 g o 100 ml.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
public class CategoriaController {

    private final CategoriaServicio categoriaServicio;

    public CategoriaController(CategoriaServicio categoriaServicio) {
        this.categoriaServicio = categoriaServicio;
    }

    @GetMapping
    @Operation(summary = "Listar categorías de un tipo",
            description = "Categorías de alimentos o de recetas en su orden de presentación, con su categoría padre.")
    @ApiResponse(responseCode = "200", description = "Categorías del tipo")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public List<CategoriaResponse> listar(
            @Parameter(description = "Tipo de categoría", example = "ALIMENTO")
            @RequestParam TipoCategoria tipo) {
        return categoriaServicio.listar(tipo);
    }
}
