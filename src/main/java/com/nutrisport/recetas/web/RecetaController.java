package com.nutrisport.recetas.web;

import com.nutrisport.catalogo.dto.CategoriaResponse;
import com.nutrisport.catalogo.entidad.TipoCategoria;
import com.nutrisport.catalogo.servicio.CategoriaServicio;
import com.nutrisport.catalogo.servicio.ProductoServicio;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.integracion.comun.ProveedorExterno;
import com.nutrisport.recetas.dto.BusquedaRecetasResponse;
import com.nutrisport.recetas.dto.NutricionRecetaResponse;
import com.nutrisport.recetas.dto.RecetaResponse;
import com.nutrisport.recetas.dto.RecetaResumenResponse;
import com.nutrisport.recetas.servicio.RecetaServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo de recetas (§7.3, Ampliación). Buscar, ver categorías y el detalle: cualquier usuario autenticado
 * (D/A). Importar de TheMealDB, relacionadas y favoritas: solo deportistas (D).
 */
@RestController
@RequestMapping("/api/v1/recetas")
@Tag(name = "Recetas", description = "Catálogo de recetas propias e importadas de TheMealDB: búsqueda, detalle con "
        + "cantidades por raciones, relacionadas y favoritas. Las de TheMealDB están en inglés, con raciones "
        + "supuestas y atribución (\"Receta e imagen: TheMealDB\"); sus nutrientes se calculan con los ingredientes.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
public class RecetaController {

    private final RecetaServicio recetaServicio;
    private final CategoriaServicio categoriaServicio;

    public RecetaController(RecetaServicio recetaServicio, CategoriaServicio categoriaServicio) {
        this.recetaServicio = recetaServicio;
        this.categoriaServicio = categoriaServicio;
    }

    @GetMapping
    @Operation(summary = "Buscar recetas",
            description = "Recetas del catálogo (públicas publicadas y propias) paginadas y, en la primera página, "
                    + "las de TheMealDB aún no importadas (estadoNutricional PENDIENTE, con referenciaExterna). "
                    + "TheMealDB se consulta con el texto o, si no, con el ingrediente, la categoría o el área; no se "
                    + "consulta si se filtra por franja, kcal o proteína, que solo se aplican a recetas con cálculo "
                    + "COMPLETO o APROXIMADO. Si TheMealDB no está disponible, themealdbDisponible = false y la "
                    + "cabecera X-Proveedor-No-Disponible.")
    @ApiResponse(responseCode = "200", description = "Recetas encontradas",
            headers = @Header(name = ProductoServicio.CABECERA_NO_DISPONIBLE,
                    description = "Solo si TheMealDB no está disponible: THEMEALDB"))
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public ResponseEntity<BusquedaRecetasResponse> buscar(
            @Parameter(description = "Texto contenido en el nombre (máximo 60 caracteres)", example = "pollo")
            @RequestParam(required = false) String q,
            @Parameter(description = "Ingrediente: nombre original en inglés o alimento del catálogo", example = "chicken")
            @RequestParam(required = false) String ingrediente,
            @Parameter(description = "Categoría de receta (GET /recetas/categorias)")
            @RequestParam(required = false) UUID categoria,
            @Parameter(description = "Cocina de TheMealDB, en inglés", example = "Moroccan")
            @RequestParam(required = false) String area,
            @Parameter(description = "todas (por defecto), propia o themealdb", example = "todas")
            @RequestParam(required = false) String origen,
            @Parameter(description = "Solo recetas aptas para esa comida del día")
            @RequestParam(required = false) TipoComida franja,
            @Parameter(description = "Máximo de kcal por ración (solo recetas con cálculo)", example = "600")
            @RequestParam(required = false) BigDecimal maxKcal,
            @Parameter(description = "Mínimo de proteína por ración en gramos (solo recetas con cálculo)", example = "30")
            @RequestParam(required = false) BigDecimal minProteina,
            @ParameterObject @PageableDefault(size = 20) Pageable paginacion) {
        BusquedaRecetasResponse busqueda = recetaServicio.buscar(q, ingrediente, categoria, area, origen, franja,
                maxKcal, minProteina, paginacion);
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.ok();
        if (!busqueda.themealdbDisponible()) {
            respuesta.header(ProductoServicio.CABECERA_NO_DISPONIBLE, ProveedorExterno.THEMEALDB.name());
        }
        return respuesta.body(busqueda);
    }

    @GetMapping("/categorias")
    @Operation(summary = "Categorías de receta",
            description = "Categorías de recetas en castellano, en su orden. codigoExterno es la categoría "
                    + "equivalente de TheMealDB (p. ej. Chicken para Pollo).")
    @ApiResponse(responseCode = "200", description = "Categorías de receta")
    public List<CategoriaResponse> categorias() {
        return categoriaServicio.listar(TipoCategoria.RECETA);
    }

    @GetMapping("/favoritas")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Mis recetas favoritas", description = "Las marcadas más recientemente primero.")
    @ApiResponse(responseCode = "200", description = "Recetas favoritas")
    @ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
    public List<RecetaResumenResponse> favoritas() {
        return recetaServicio.favoritas();
    }

    @GetMapping("/externas/themealdb/{idMeal}")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Detalle de una receta de TheMealDB",
            description = "La importa la primera vez (idioma en, 4 raciones supuestas, sin publicar, ingredientes "
                    + "pendientes de emparejar) y devuelve su detalle con el id propio; después sale del catálogo sin "
                    + "llamar a TheMealDB.")
    @ApiResponse(responseCode = "200", description = "Receta importada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    @ApiResponse(responseCode = "503", ref = RespuestasApi.SERVICIO_NO_DISPONIBLE)
    public RecetaResponse importar(
            @Parameter(description = "idMeal de TheMealDB", example = "52850") @PathVariable String idMeal) {
        return recetaServicio.importarDeTheMealDb(idMeal);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una receta",
            description = "Ingredientes con las cantidades para las raciones pedidas, instrucciones, nutrientes por "
                    + "ración (si hay cálculo), alérgenos y si es compatible con tus restricciones.")
    @ApiResponse(responseCode = "200", description = "La receta")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public RecetaResponse detalle(@PathVariable UUID id,
            @Parameter(description = "Raciones para las cantidades (1–20); por defecto, las de la receta", example = "2")
            @RequestParam(required = false) Integer raciones) {
        return recetaServicio.detalle(id, raciones);
    }

    @GetMapping("/{id}/nutricion")
    @Operation(summary = "Desglose del cálculo nutricional",
            description = "Cada ingrediente con sus gramos para las raciones pedidas, cómo se han obtenido (EXACTA, "
                    + "ESTIMADA, DESPRECIABLE o SIN_DATOS) y la fuente de sus nutrientes; nutrientes por ración (nulos "
                    + "si el cálculo es INCOMPLETO), estado y completitud. Las raciones de TheMealDB son supuestas.")
    @ApiResponse(responseCode = "200", description = "Desglose del cálculo")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public NutricionRecetaResponse nutricion(@PathVariable UUID id,
            @Parameter(description = "Raciones para las cantidades (1–20); por defecto, las de la receta", example = "2")
            @RequestParam(required = false) Integer raciones) {
        return recetaServicio.nutricion(id, raciones);
    }

    @GetMapping("/{id}/relacionadas")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Recetas relacionadas", description = "Hasta 6 de la misma categoría o con el mismo primer "
            + "ingrediente.")
    @ApiResponse(responseCode = "200", description = "Recetas relacionadas")
    @ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public List<RecetaResumenResponse> relacionadas(@PathVariable UUID id) {
        return recetaServicio.relacionadas(id);
    }

    @PutMapping("/{id}/favorita")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Marcar como favorita", description = "Si ya lo era, no cambia nada.")
    @ApiResponse(responseCode = "204", description = "Receta favorita")
    @ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ResponseEntity<Void> marcarFavorita(@PathVariable UUID id) {
        recetaServicio.marcarFavorita(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/favorita")
    @PreAuthorize("hasRole('DEPORTISTA')")
    @Operation(summary = "Quitar de favoritas", description = "Si no lo era, no cambia nada.")
    @ApiResponse(responseCode = "204", description = "Receta quitada de favoritas")
    @ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ResponseEntity<Void> quitarFavorita(@PathVariable UUID id) {
        recetaServicio.quitarFavorita(id);
        return ResponseEntity.noContent().build();
    }
}
