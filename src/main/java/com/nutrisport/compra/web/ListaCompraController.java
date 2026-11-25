package com.nutrisport.compra.web;

import com.nutrisport.compra.dto.ActualizarListaRequest;
import com.nutrisport.compra.dto.ActualizarProductoRequest;
import com.nutrisport.compra.dto.AnadirRecetaListaRequest;
import com.nutrisport.compra.dto.GenerarListaRequest;
import com.nutrisport.compra.dto.ListaCompraResponse;
import com.nutrisport.compra.dto.ListaCompraResumenResponse;
import com.nutrisport.compra.dto.ProductoCompraRequest;
import com.nutrisport.compra.servicio.ListaCompraServicio;
import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.comun.dto.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Listas de la compra del deportista autenticado (§3.E y §7.6, Ampliación). Las de otro usuario, 404.
 */
@RestController
@RequestMapping("/api/v1/listas-compra")
@PreAuthorize("hasRole('DEPORTISTA')")
@Tag(name = "Compra", description = "Listas generadas desde un menú con las cantidades sumadas por "
        + "alimento y agrupadas por sección del supermercado, productos libres, ingredientes de una receta y "
        + "progreso de la compra.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
@ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
public class ListaCompraController {

    private static final String RUTA = "/api/v1/listas-compra/";

    private static final String EJEMPLO_GENERAR = """
            {
              "desde": "2026-11-30",
              "hasta": "2026-12-02"
            }""";

    private static final String EJEMPLO_ACTUALIZAR = """
            {
              "nombre": "Compra del lunes",
              "estado": "COMPLETADA"
            }""";

    private static final String EJEMPLO_PRODUCTO = """
            {
              "nombre": "Servilletas",
              "cantidad": 1,
              "unidad": "UD",
              "seccion": "Otros"
            }""";

    private static final String EJEMPLO_RECETA = """
            {
              "recetaId": "0195f3a2-7c1e-7b8a-9d40-2f6a1c3e5b70",
              "raciones": 4
            }""";

    private static final String EJEMPLO_ACTUALIZAR_PRODUCTO = """
            {
              "comprado": true
            }""";

    private final ListaCompraServicio listaCompraServicio;

    public ListaCompraController(ListaCompraServicio listaCompraServicio) {
        this.listaCompraServicio = listaCompraServicio;
    }

    @GetMapping
    @Operation(summary = "Mis listas de la compra",
            description = "Resumen de las listas con su progreso (comprados de total), la más reciente primero "
                    + "(como mucho 50 por página).")
    @ApiResponse(responseCode = "200", description = "Listas del usuario")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public PaginaResponse<ListaCompraResumenResponse> listar(
            @ParameterObject @PageableDefault(size = 20) Pageable paginacion) {
        return listaCompraServicio.listar(paginacion);
    }

    @PostMapping("/desde-menu/{menuId}")
    @Operation(summary = "Generar una lista desde un menú",
            description = "Suma los ingredientes emparejados con el catálogo de las comidas no consumidas del menú "
                    + "(cantidad de la receta × raciones / raciones de la receta) por alimento, en G o ML (KG o L "
                    + "desde 1000), con la sección de su categoría y el desglose por día. Sin cuerpo o sin fechas, el "
                    + "menú completo; desde y hasta deben estar dentro del menú (400). Si no hay nada que comprar, la "
                    + "lista se crea vacía.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = GenerarListaRequest.class),
                    examples = @ExampleObject(name = "Rango", summary = "De lunes a miércoles",
                            value = EJEMPLO_GENERAR))))
    @ApiResponse(responseCode = "201", description = "Lista generada; Location apunta a ella")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ResponseEntity<ListaCompraResponse> generarDesdeMenu(@PathVariable UUID menuId,
            @Valid @RequestBody(required = false) GenerarListaRequest peticion) {
        ListaCompraResponse lista = listaCompraServicio.generarDesdeMenu(menuId, peticion);
        return ResponseEntity.created(URI.create(RUTA + lista.id())).body(lista);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una lista",
            description = "Productos agrupados por sección en el orden de las categorías de alimentos (las secciones "
                    + "propias, al final); dentro de cada sección, los comprados al final.")
    @ApiResponse(responseCode = "200", description = "La lista")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ListaCompraResponse obtener(@PathVariable UUID id) {
        return listaCompraServicio.obtener(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Renombrar o completar una lista",
            description = "Solo cambian los campos presentes; devuelve la lista completa.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ActualizarListaRequest.class),
                    examples = @ExampleObject(name = "Lista", summary = "Renombrar y completar",
                            value = EJEMPLO_ACTUALIZAR))))
    @ApiResponse(responseCode = "200", description = "Lista actualizada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ListaCompraResponse actualizar(@PathVariable UUID id, @Valid @RequestBody ActualizarListaRequest peticion) {
        return listaCompraServicio.actualizar(id, peticion);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una lista", description = "La borra con sus productos.")
    @ApiResponse(responseCode = "204", description = "Lista eliminada")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        listaCompraServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/productos")
    @Operation(summary = "Añadir un producto libre",
            description = "Producto escrito a mano (manual = true) con su cantidad y unidad. Sin sección va a "
                    + "\"Otros\". Devuelve la lista completa.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ProductoCompraRequest.class),
                    examples = @ExampleObject(name = "Producto", summary = "Un producto que no es un alimento",
                            value = EJEMPLO_PRODUCTO))))
    @ApiResponse(responseCode = "201", description = "Producto añadido; devuelve la lista")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ResponseEntity<ListaCompraResponse> anadirProducto(@PathVariable UUID id,
            @Valid @RequestBody ProductoCompraRequest peticion) {
        ListaCompraResponse lista = listaCompraServicio.anadirProducto(id, peticion);
        return ResponseEntity.created(URI.create(RUTA + id)).body(lista);
    }

    @PostMapping("/{id}/productos/desde-receta")
    @Operation(summary = "Añadir los ingredientes de una receta",
            description = "Escala los ingredientes a raciones / raciones de la receta. Los emparejados con el "
                    + "catálogo se suman al producto pendiente del mismo alimento (o se crea uno); los demás se "
                    + "añaden como texto libre con su medida original, en 1 UD. 404 si la receta no existe o es "
                    + "privada de otro usuario.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = AnadirRecetaListaRequest.class),
                    examples = @ExampleObject(name = "Receta", summary = "Para 4 raciones",
                            value = EJEMPLO_RECETA))))
    @ApiResponse(responseCode = "201", description = "Ingredientes añadidos; devuelve la lista")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ResponseEntity<ListaCompraResponse> anadirDesdeReceta(@PathVariable UUID id,
            @Valid @RequestBody AnadirRecetaListaRequest peticion) {
        ListaCompraResponse lista = listaCompraServicio.anadirDesdeReceta(id, peticion);
        return ResponseEntity.created(URI.create(RUTA + id)).body(lista);
    }

    @PatchMapping("/{id}/productos/{productoId}")
    @Operation(summary = "Editar o marcar un producto",
            description = "Cambia la cantidad, la unidad o si está comprado; los campos ausentes no cambian. "
                    + "Devuelve la lista completa.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ActualizarProductoRequest.class),
                    examples = @ExampleObject(name = "Comprado", summary = "Marcar como comprado",
                            value = EJEMPLO_ACTUALIZAR_PRODUCTO))))
    @ApiResponse(responseCode = "200", description = "Lista con el producto cambiado")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ListaCompraResponse actualizarProducto(@PathVariable UUID id, @PathVariable UUID productoId,
            @Valid @RequestBody ActualizarProductoRequest peticion) {
        return listaCompraServicio.actualizarProducto(id, productoId, peticion);
    }

    @DeleteMapping("/{id}/productos/{productoId}")
    @Operation(summary = "Quitar un producto", description = "Lo borra de la lista, esté comprado o no.")
    @ApiResponse(responseCode = "204", description = "Producto quitado")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ResponseEntity<Void> eliminarProducto(@PathVariable UUID id, @PathVariable UUID productoId) {
        listaCompraServicio.eliminarProducto(id, productoId);
        return ResponseEntity.noContent().build();
    }
}
