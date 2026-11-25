package com.nutrisport.comidas.web;

import com.nutrisport.comidas.dto.ComidasDiaResponse;
import com.nutrisport.comidas.dto.DuplicarComidaRequest;
import com.nutrisport.comidas.dto.RegistroComidaRequest;
import com.nutrisport.comidas.dto.RegistroComidaResponse;
import com.nutrisport.comidas.entidad.OrigenRegistro;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comidas.servicio.ComidaServicio;
import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.menus.dto.RegistrarRecetaRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
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
 * Registro de comidas e historial del deportista autenticado (§7.4). Son datos de salud: con el
 * consentimiento revocado responden 403 CONSENTIMIENTO_REVOCADO (WebConfig).
 */
@RestController
@RequestMapping("/api/v1/comidas")
@PreAuthorize("hasRole('DEPORTISTA')")
@Tag(name = "Comidas", description = "Registro manual de comidas, historial por días, edición y duplicado. "
        + "Las cantidades se convierten a gramos y los nutrientes se guardan como instantánea del catálogo.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
@ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
public class ComidaController {

    private static final String EJEMPLO_COMIDA = """
            {
              "fecha": "2026-11-24",
              "hora": "14:30",
              "tipoComida": "COMIDA",
              "notas": "Después del rodaje largo",
              "alimentos": [
                {"alimentoId": "3f8b2c1e-6a4d-4e2b-9c7a-1d2e3f4a5b6c", "cantidad": 150, "unidad": "g"},
                {"alimentoId": "7a1c9e2d-4b3f-4a6e-8d2c-5e6f7a8b9c0d", "cantidad": 1, "unidad": "plato"}
              ]
            }""";

    private static final String EJEMPLO_DESDE_RECETA = """
            {
              "recetaId": "0195f3a2-7c1e-7b8a-9d40-2f6a1c3e5b70",
              "raciones": 1.5,
              "fecha": "2026-11-24",
              "tipoComida": "COMIDA"
            }""";

    private final ComidaServicio comidaServicio;

    public ComidaController(ComidaServicio comidaServicio) {
        this.comidaServicio = comidaServicio;
    }

    @GetMapping
    @Operation(summary = "Historial de comidas por días",
            description = "Días con comidas entre desde y hasta (ambos incluidos, máximo 92 días), opcionalmente "
                    + "de un tipo de comida, con los totales de cada día. Los días sin comidas no aparecen.")
    @ApiResponse(responseCode = "200", description = "Días con sus comidas y totales, en orden cronológico")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public List<ComidasDiaResponse> listar(
            @Parameter(description = "Primer día (incluido)", example = "2026-11-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Último día (incluido); como mucho 92 días después de desde", example = "2026-11-24")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(description = "Solo las comidas de este tipo")
            @RequestParam(required = false) TipoComida tipo) {
        return comidaServicio.listar(desde, hasta, tipo);
    }

    @GetMapping("/dia/{fecha}")
    @Operation(summary = "Comidas y totales de un día",
            description = "Comidas del día en orden cronológico con sus avisos y la suma de sus nutrientes.")
    @ApiResponse(responseCode = "200", description = "Comidas del día y totales")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public ComidasDiaResponse dia(
            @Parameter(description = "Día (AAAA-MM-DD)", example = "2026-11-24")
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return comidaServicio.dia(fecha);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una comida", description = "Con sus alimentos, totales y avisos.")
    @ApiResponse(responseCode = "200", description = "La comida")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public RegistroComidaResponse obtener(@PathVariable UUID id) {
        return comidaServicio.obtener(id);
    }

    @PostMapping
    @Operation(summary = "Registrar una comida",
            description = "Hasta 40 alimentos. Cantidades en g, kg, ml, l o en una porción casera del alimento "
                    + "(p. ej. \"plato\"), hasta 5 kg por alimento. Se admiten fechas pasadas y hasta mañana. "
                    + "Devuelve avisos si algún alimento choca con las restricciones del usuario, sin impedir "
                    + "el registro.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = RegistroComidaRequest.class),
                    examples = @ExampleObject(name = "Comida", summary = "Comida con dos alimentos",
                            value = EJEMPLO_COMIDA))))
    @ApiResponse(responseCode = "201", description = "Comida registrada; Location apunta a ella")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public ResponseEntity<RegistroComidaResponse> crear(@Valid @RequestBody RegistroComidaRequest peticion) {
        RegistroComidaResponse creada = comidaServicio.crear(peticion, OrigenRegistro.MANUAL);
        return ResponseEntity.created(URI.create("/api/v1/comidas/" + creada.id())).body(creada);
    }

    @PostMapping("/desde-receta")
    @Operation(summary = "Registrar una receta como comida",
            description = "Crea una comida con origen RECETA con los ingredientes de la receta emparejados con el "
                    + "catálogo, con los gramos escalados a raciones / raciones de la receta. Se admiten fechas "
                    + "pasadas y hasta mañana. 404 si la receta no existe o es privada de otro usuario; 422 "
                    + "NUTRICION_INCOMPLETA si su cálculo es INCOMPLETO o no tiene ingredientes emparejados.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = RegistrarRecetaRequest.class),
                    examples = @ExampleObject(name = "Receta", summary = "Ración y media de una receta",
                            value = EJEMPLO_DESDE_RECETA))))
    @ApiResponse(responseCode = "201", description = "Comida registrada; Location apunta a ella")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    @ApiResponse(responseCode = "422", ref = RespuestasApi.REGLA_NEGOCIO)
    public ResponseEntity<RegistroComidaResponse> registrarDesdeReceta(
            @Valid @RequestBody RegistrarRecetaRequest peticion) {
        RegistroComidaResponse creada = comidaServicio.registrarDesdeReceta(peticion.recetaId(), peticion.raciones(),
                peticion.fecha(), peticion.tipoComida());
        return ResponseEntity.created(URI.create("/api/v1/comidas/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar una comida",
            description = "Sustituye sus datos y todos sus alimentos (mismas reglas que al registrarla).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = RegistroComidaRequest.class),
                    examples = @ExampleObject(name = "Comida", summary = "Comida con dos alimentos",
                            value = EJEMPLO_COMIDA))))
    @ApiResponse(responseCode = "200", description = "Comida actualizada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public RegistroComidaResponse editar(@PathVariable UUID id, @Valid @RequestBody RegistroComidaRequest peticion) {
        return comidaServicio.editar(id, peticion);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una comida", description = "Borra la comida y sus alimentos.")
    @ApiResponse(responseCode = "204", description = "Comida eliminada")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        comidaServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicar")
    @Operation(summary = "Duplicar una comida en otra fecha o tipo",
            description = "Crea una comida nueva con origen COPIA y los nutrientes actuales del catálogo. Sin "
                    + "tipoComida se conserva el de la original.")
    @ApiResponse(responseCode = "201", description = "Copia creada; Location apunta a ella")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ResponseEntity<RegistroComidaResponse> duplicar(@PathVariable UUID id,
            @Valid @RequestBody DuplicarComidaRequest peticion) {
        RegistroComidaResponse copia = comidaServicio.duplicar(id, peticion);
        return ResponseEntity.created(URI.create("/api/v1/comidas/" + copia.id())).body(copia);
    }
}
