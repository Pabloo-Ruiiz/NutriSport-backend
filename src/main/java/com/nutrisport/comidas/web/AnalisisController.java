package com.nutrisport.comidas.web;

import com.nutrisport.comidas.dto.AnalisisResponse;
import com.nutrisport.comidas.dto.ConfirmarAnalisisRequest;
import com.nutrisport.comidas.dto.RegistroComidaResponse;
import com.nutrisport.comidas.dto.UrlImagenResponse;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comidas.servicio.AnalisisImagenServicio;
import com.nutrisport.comun.documentacion.RespuestasApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Análisis de fotos de comidas con IA (§7.4). Son datos de salud: con el consentimiento revocado
 * responden 403 CONSENTIMIENTO_REVOCADO (WebConfig). Los análisis de otro usuario responden 404.
 */
@RestController
@RequestMapping("/api/v1/analisis")
@PreAuthorize("hasRole('DEPORTISTA')")
@Tag(name = "Análisis", description = "Reconocimiento de alimentos en fotos con IA: subida, consulta del resultado "
        + "(polling), confirmación como comida y descarte. Los valores son estimaciones orientativas; la IA solo "
        + "identifica alimentos y gramos, y los nutrientes salen del catálogo.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
@ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
public class AnalisisController {

    private static final String EJEMPLO_CONFIRMACION = """
            {
              "fecha": "2026-11-24",
              "hora": "21:00",
              "tipoComida": "CENA",
              "alimentos": [
                {"alimentoId": "3f8b2c1e-6a4d-4e2b-9c7a-1d2e3f4a5b6c", "gramos": 160, "indiceCandidato": 0},
                {"alimentoId": "7a1c9e2d-4b3f-4a6e-8d2c-5e6f7a8b9c0d", "gramos": 150, "indiceCandidato": 1},
                {"alimentoId": "9c2e4f6a-8b1d-4c3e-a5f7-0b1c2d3e4f5a", "gramos": 30}
              ]
            }""";

    private final AnalisisImagenServicio analisisServicio;

    public AnalisisController(AnalisisImagenServicio analisisServicio) {
        this.analisisServicio = analisisServicio;
    }

    @PostMapping(path = "/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir una foto para analizarla",
            description = "JPEG o PNG de hasta 10 MB y 40 megapíxeles (se valida la firma de bytes). La foto se "
                    + "recodifica sin metadatos EXIF antes de guardarla o enviarla a la IA. Requiere el email "
                    + "verificado (403 EMAIL_NO_VERIFICADO) y tiene un límite diario (429). Responde 202 con el "
                    + "análisis PENDIENTE: consulta su estado en la URL de Location.")
    @ApiResponse(responseCode = "202", description = "Análisis creado y pendiente de procesar",
            headers = @Header(name = HttpHeaders.LOCATION, description = "URL del análisis para consultar su estado"))
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "413", ref = RespuestasApi.ARCHIVO_DEMASIADO_GRANDE)
    @ApiResponse(responseCode = "415", ref = RespuestasApi.TIPO_NO_ADMITIDO)
    @ApiResponse(responseCode = "429", ref = RespuestasApi.LIMITE_EXCEDIDO)
    @ApiResponse(responseCode = "503", ref = RespuestasApi.SERVICIO_NO_DISPONIBLE)
    public ResponseEntity<AnalisisResponse> crear(
            @Parameter(description = "Foto de la comida (JPEG o PNG). No fotografíes personas ni documentos.")
            @RequestPart("imagen") MultipartFile imagen,
            @Parameter(description = "Tipo de comida", example = "COMIDA")
            @RequestParam TipoComida tipoComida,
            @Parameter(description = "Pista opcional para la IA, como máximo 500 caracteres",
                    example = "arroz con pollo, sin salsa")
            @RequestParam(required = false) String descripcion) {
        AnalisisResponse analisis = analisisServicio.crear(imagen, tipoComida, descripcion);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/analisis/" + analisis.id()))
                .body(analisis);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Estado y resultado de un análisis",
            description = "Consultar hasta que el estado deje de ser PENDIENTE o PROCESANDO. COMPLETADO trae los "
                    + "candidatos con sus coincidencias del catálogo y el total estimado; FALLIDO trae errorMensaje "
                    + "(p. ej. \"No hemos detectado alimentos\").")
    @ApiResponse(responseCode = "200", description = "El análisis")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public AnalisisResponse obtener(@PathVariable UUID id) {
        return analisisServicio.obtener(id);
    }

    @GetMapping("/{id}/imagen")
    @Operation(summary = "URL prefirmada de la foto del análisis",
            description = "URL de lectura temporal (caduca a los pocos minutos) de la foto reprocesada. 404 si la "
                    + "foto ya se borró por la política de retención.")
    @ApiResponse(responseCode = "200", description = "URL y momento en que caduca")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public UrlImagenResponse imagen(@PathVariable UUID id) {
        return analisisServicio.urlImagen(id);
    }

    @PostMapping("/{id}/confirmar")
    @Operation(summary = "Confirmar un análisis y registrar la comida",
            description = "Registra una comida con origen FOTO con los alimentos que el usuario ha revisado: puede "
                    + "aceptar, corregir los gramos, cambiar el alimento, quitar candidatos o añadir alimentos "
                    + "(sin indiceCandidato). Cada candidato se confirma como mucho una vez. Solo para análisis "
                    + "COMPLETADO (409 ANALISIS_NO_CONFIRMABLE).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ConfirmarAnalisisRequest.class),
                    examples = @ExampleObject(name = "Confirmación",
                            summary = "Dos candidatos (uno con los gramos corregidos) y un alimento añadido",
                            value = EJEMPLO_CONFIRMACION))))
    @ApiResponse(responseCode = "201", description = "Comida registrada")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    @ApiResponse(responseCode = "409", ref = RespuestasApi.CONFLICTO)
    public ResponseEntity<RegistroComidaResponse> confirmar(@PathVariable UUID id,
            @Valid @RequestBody ConfirmarAnalisisRequest peticion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(analisisServicio.confirmar(id, peticion));
    }

    @PostMapping("/{id}/descartar")
    @Operation(summary = "Descartar un análisis",
            description = "Para análisis COMPLETADO o FALLIDO (409 ANALISIS_NO_DESCARTABLE en otro estado). La foto "
                    + "se borra con la limpieza periódica de imágenes de los análisis terminados.")
    @ApiResponse(responseCode = "204", description = "Análisis descartado")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    @ApiResponse(responseCode = "409", ref = RespuestasApi.CONFLICTO)
    public ResponseEntity<Void> descartar(@PathVariable UUID id) {
        analisisServicio.descartar(id);
        return ResponseEntity.noContent().build();
    }
}
