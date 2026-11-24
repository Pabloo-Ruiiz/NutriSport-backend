package com.nutrisport.catalogo.web;

import com.nutrisport.catalogo.dto.ProductoResponse;
import com.nutrisport.catalogo.dto.ProductoResumenResponse;
import com.nutrisport.catalogo.servicio.ProductoServicio;
import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.integracion.comun.ProveedorExterno;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Productos comerciales de Open Food Facts (§7.3, Ampliación). Angular nunca llama al proveedor: solo a
 * estos endpoints, con DTO propios (docs/API_INTEGRATIONS.md §6.1).
 */
@RestController
@RequestMapping("/api/v1/productos")
@PreAuthorize("hasRole('DEPORTISTA')")
@Tag(name = "Productos", description = "Productos comerciales de Open Food Facts: búsqueda bajo demanda y ficha "
        + "por código de barras. El catálogo hace de caché (30 días) y la aplicación sigue funcionando con él si "
        + "el proveedor no responde. Datos de Open Food Facts (ODbL), con atribución en cada producto.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
@ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
public class ProductoController {

    private final ProductoServicio productoServicio;

    public ProductoController(ProductoServicio productoServicio) {
        this.productoServicio = productoServicio;
    }

    @GetMapping
    @Operation(summary = "Buscar productos comerciales",
            description = "Busca en Open Food Facts, 20 por página. Llámalo solo cuando el usuario lo pida (botón "
                    + "\"Buscar en productos comerciales\"), nunca mientras escribe: el límite de búsquedas lo "
                    + "comparten todos los usuarios. Caché de 1 hora. Los resultados no se guardan; los que ya están "
                    + "en el catálogo llevan id. Si el proveedor no está disponible, responde 200 con una lista "
                    + "vacía y la cabecera X-Proveedor-No-Disponible.")
    @ApiResponse(responseCode = "200", description = "Productos encontrados",
            headers = @Header(name = ProductoServicio.CABECERA_NO_DISPONIBLE,
                    description = "Solo si el proveedor no está disponible: OPEN_FOOD_FACTS (la lista va vacía)"))
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public ResponseEntity<List<ProductoResumenResponse>> buscar(
            @Parameter(description = "Texto a buscar, de 2 a 60 caracteres", example = "nutella")
            @RequestParam(required = false) String q,
            @Parameter(description = "Página, desde 0 (como máximo 49)", example = "0")
            @RequestParam(defaultValue = "0") int page) {
        ProductoServicio.Busqueda busqueda = productoServicio.buscar(q, page);
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.ok();
        if (!busqueda.proveedorDisponible()) {
            respuesta.header(ProductoServicio.CABECERA_NO_DISPONIBLE, ProveedorExterno.OPEN_FOOD_FACTS.name());
        }
        return respuesta.body(busqueda.productos());
    }

    @GetMapping("/{codigoBarras}")
    @Operation(summary = "Producto por código de barras",
            description = "Busca primero en el catálogo; si no está o su copia tiene más de 30 días, lo pide a Open "
                    + "Food Facts y lo guarda. Si el proveedor no responde, devuelve la copia del catálogo aunque sea "
                    + "antigua. Un producto con la información nutricional incompleta se devuelve sin id y con "
                    + "nutricionCompleta = false: no se puede registrar en una comida. Los alérgenos de los productos "
                    + "importados están sin revisar.")
    @ApiResponse(responseCode = "200", description = "Ficha del producto")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    @ApiResponse(responseCode = "409", ref = RespuestasApi.CONFLICTO)
    @ApiResponse(responseCode = "503", ref = RespuestasApi.SERVICIO_NO_DISPONIBLE)
    public ProductoResponse porCodigo(
            @Parameter(description = "Código de barras (EAN-8, EAN-13, UPC...): de 8 a 14 dígitos",
                    example = "3017624010701")
            @PathVariable String codigoBarras) {
        return productoServicio.porCodigo(codigoBarras);
    }
}
