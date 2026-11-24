package com.nutrisport.admin.web;

import com.nutrisport.admin.dto.RevisionAlergenosRequest;
import com.nutrisport.admin.servicio.AdminCatalogoServicio;
import com.nutrisport.catalogo.dto.AlimentoRequest;
import com.nutrisport.catalogo.dto.AlimentoResponse;
import com.nutrisport.catalogo.dto.ResultadoImportacion;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.comun.dto.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Gestión del catálogo de alimentos por un administrador (§7.10). */
@RestController
@RequestMapping("/api/v1/admin/alimentos")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administración: alimentos", description = "Catálogo público, alérgenos e importación CSV")
public class AdminAlimentoController {

    private final AdminCatalogoServicio adminCatalogoServicio;

    public AdminAlimentoController(AdminCatalogoServicio adminCatalogoServicio) {
        this.adminCatalogoServicio = adminCatalogoServicio;
    }

    @GetMapping
    @Operation(summary = "Todo el catálogo",
            description = "Incluye los alimentos privados de usuarios y los desactivados. Filtros opcionales.")
    public PaginaResponse<AlimentoResponse> buscar(@RequestParam(required = false) String q,
            @RequestParam(required = false) FuenteDato fuente,
            @RequestParam(required = false) Boolean revisados,
            @RequestParam(required = false) Visibilidad visibilidad,
            @RequestParam(required = false) Boolean activo,
            @ParameterObject @PageableDefault(size = 20) Pageable paginacion) {
        return adminCatalogoServicio.buscar(q, fuente, revisados, visibilidad, activo, paginacion);
    }

    @PostMapping
    @Operation(summary = "Crear un alimento público",
            description = "La fuente es obligatoria (trazabilidad). Los alérgenos quedan sin revisar.")
    public ResponseEntity<AlimentoResponse> crear(@Valid @RequestBody AlimentoRequest peticion,
            @RequestParam FuenteDato fuente,
            @RequestParam(required = false) String referenciaExterna) {
        AlimentoResponse creado = adminCatalogoServicio.crearAlimento(peticion, fuente, referenciaExterna);
        return ResponseEntity.created(URI.create("/api/v1/alimentos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar cualquier alimento",
            description = "Si cambian los alérgenos o la lactosa, vuelven a quedar sin revisar.")
    public AlimentoResponse editar(@PathVariable UUID id, @Valid @RequestBody AlimentoRequest peticion) {
        return adminCatalogoServicio.editarAlimento(id, peticion);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un alimento", description = "Si ya se ha usado en comidas, solo se desactiva.")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        adminCatalogoServicio.eliminarAlimento(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importar alimentos desde CSV",
            description = "Upsert por nombre, marca y preparación; alérgenos sin revisar. Máximo 5000 filas.")
    public ResultadoImportacion importar(@RequestPart("archivo") MultipartFile archivo,
            @RequestParam FuenteDato fuente,
            @RequestParam(required = false) String referencia) {
        return adminCatalogoServicio.importarCsv(archivo, fuente, referencia);
    }

    @PutMapping("/{id}/alergenos")
    @Operation(summary = "Revisar los alérgenos de un alimento", description = "Los sustituye y los marca como revisados.")
    public AlimentoResponse revisarAlergenos(@PathVariable UUID id,
            @Valid @RequestBody RevisionAlergenosRequest peticion) {
        return adminCatalogoServicio.revisarAlergenos(id, peticion);
    }
}
