package com.nutrisport.comidas.web;

import com.nutrisport.comidas.dto.AnalisisResponse;
import com.nutrisport.comidas.dto.UrlImagenResponse;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comidas.servicio.AnalisisImagenServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
@Tag(name = "Análisis de fotos", description = "Reconocimiento de alimentos en fotos con IA")
public class AnalisisController {

    private final AnalisisImagenServicio analisisServicio;

    public AnalisisController(AnalisisImagenServicio analisisServicio) {
        this.analisisServicio = analisisServicio;
    }

    @PostMapping(path = "/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Subir una foto para analizarla",
            description = "JPEG o PNG de hasta 10 MB. Responde 202 con el análisis PENDIENTE; consulta su estado en "
                    + "la URL de Location. 403 EMAIL_NO_VERIFICADO, 429 LIMITE_EXCEDIDO, 415 ARCHIVO_NO_VALIDO.")
    public ResponseEntity<AnalisisResponse> crear(@RequestPart("imagen") MultipartFile imagen,
            @RequestParam TipoComida tipoComida,
            @RequestParam(required = false) String descripcion) {
        AnalisisResponse analisis = analisisServicio.crear(imagen, tipoComida, descripcion);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/analisis/" + analisis.id()))
                .body(analisis);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Estado y resultado de un análisis",
            description = "Consultar hasta que el estado deje de ser PENDIENTE o PROCESANDO.")
    public AnalisisResponse obtener(@PathVariable UUID id) {
        return analisisServicio.obtener(id);
    }

    @GetMapping("/{id}/imagen")
    @Operation(summary = "URL prefirmada de la foto del análisis", description = "Caduca a los pocos minutos.")
    public UrlImagenResponse imagen(@PathVariable UUID id) {
        return analisisServicio.urlImagen(id);
    }
}
