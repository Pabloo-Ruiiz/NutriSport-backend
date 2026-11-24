package com.nutrisport.comidas.web;

import com.nutrisport.comidas.dto.ComidasDiaResponse;
import com.nutrisport.comidas.dto.DuplicarComidaRequest;
import com.nutrisport.comidas.dto.RegistroComidaRequest;
import com.nutrisport.comidas.dto.RegistroComidaResponse;
import com.nutrisport.comidas.entidad.OrigenRegistro;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comidas.servicio.ComidaServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
@Tag(name = "Comidas", description = "Registro manual de comidas, historial y duplicado")
public class ComidaController {

    private final ComidaServicio comidaServicio;

    public ComidaController(ComidaServicio comidaServicio) {
        this.comidaServicio = comidaServicio;
    }

    @GetMapping
    @Operation(summary = "Historial de comidas por días",
            description = "Días con comidas entre desde y hasta (ambos incluidos, máximo 92 días), opcionalmente "
                    + "de un tipo de comida, con los totales de cada día.")
    public List<ComidasDiaResponse> listar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) TipoComida tipo) {
        return comidaServicio.listar(desde, hasta, tipo);
    }

    @GetMapping("/dia/{fecha}")
    @Operation(summary = "Comidas y totales de un día")
    public ComidasDiaResponse dia(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return comidaServicio.dia(fecha);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una comida", description = "404 si no existe o es de otro usuario.")
    public RegistroComidaResponse obtener(@PathVariable UUID id) {
        return comidaServicio.obtener(id);
    }

    @PostMapping
    @Operation(summary = "Registrar una comida",
            description = "Cantidades en g, kg, ml, l o porciones caseras del alimento. Devuelve avisos si algún "
                    + "alimento choca con las restricciones del usuario, pero no impide el registro.")
    public ResponseEntity<RegistroComidaResponse> crear(@Valid @RequestBody RegistroComidaRequest peticion) {
        RegistroComidaResponse creada = comidaServicio.crear(peticion, OrigenRegistro.MANUAL);
        return ResponseEntity.created(URI.create("/api/v1/comidas/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar una comida", description = "Sustituye sus datos y todos sus alimentos.")
    public RegistroComidaResponse editar(@PathVariable UUID id, @Valid @RequestBody RegistroComidaRequest peticion) {
        return comidaServicio.editar(id, peticion);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una comida")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        comidaServicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicar")
    @Operation(summary = "Duplicar una comida en otra fecha o tipo",
            description = "Crea una comida nueva con origen COPIA y los nutrientes actuales del catálogo.")
    public ResponseEntity<RegistroComidaResponse> duplicar(@PathVariable UUID id,
            @Valid @RequestBody DuplicarComidaRequest peticion) {
        RegistroComidaResponse copia = comidaServicio.duplicar(id, peticion);
        return ResponseEntity.created(URI.create("/api/v1/comidas/" + copia.id())).body(copia);
    }
}
