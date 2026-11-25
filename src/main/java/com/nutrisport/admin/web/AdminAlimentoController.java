package com.nutrisport.admin.web;

import com.nutrisport.admin.dto.RevisionAlergenosRequest;
import com.nutrisport.admin.servicio.AdminCatalogoServicio;
import com.nutrisport.catalogo.dto.AlimentoRequest;
import com.nutrisport.catalogo.dto.AlimentoResponse;
import com.nutrisport.catalogo.dto.ResultadoImportacion;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.comun.dto.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = "Admin", description = "Solo para administradores: catálogo de alimentos (alta, edición, revisión de "
        + "alérgenos e importación CSV), categorías, recetas públicas y su publicación, emparejamientos de "
        + "ingredientes y usuarios (búsqueda, bloqueo y roles). Cada acción queda auditada. Nunca da acceso a datos "
        + "de salud.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
@ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
public class AdminAlimentoController {

    private final AdminCatalogoServicio adminCatalogoServicio;

    public AdminAlimentoController(AdminCatalogoServicio adminCatalogoServicio) {
        this.adminCatalogoServicio = adminCatalogoServicio;
    }

    @GetMapping
    @Operation(summary = "Todo el catálogo",
            description = "Incluye los alimentos privados de usuarios y los desactivados, ordenados por nombre. "
                    + "Todos los filtros son opcionales y se combinan. Máximo 100 por página.")
    @ApiResponse(responseCode = "200", description = "Página de alimentos")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public PaginaResponse<AlimentoResponse> buscar(
            @Parameter(description = "Texto contenido en el nombre (sin distinguir tildes ni mayúsculas)",
                    example = "arroz")
            @RequestParam(required = false) String q,
            @Parameter(description = "Origen de los datos")
            @RequestParam(required = false) FuenteDato fuente,
            @Parameter(description = "true: solo con los alérgenos revisados; false: solo pendientes de revisar")
            @RequestParam(required = false) Boolean revisados,
            @Parameter(description = "PUBLICO o PRIVADO (alimentos de usuarios)")
            @RequestParam(required = false) Visibilidad visibilidad,
            @Parameter(description = "true: solo activos; false: solo desactivados")
            @RequestParam(required = false) Boolean activo,
            @ParameterObject @PageableDefault(size = 20) Pageable paginacion) {
        return adminCatalogoServicio.buscar(q, fuente, revisados, visibilidad, activo, paginacion);
    }

    @PostMapping
    @Operation(summary = "Crear un alimento público",
            description = "Queda validado y con los alérgenos sin revisar (se revisan con PUT /{id}/alergenos).")
    @ApiResponse(responseCode = "201", description = "Alimento creado; Location apunta a su detalle")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public ResponseEntity<AlimentoResponse> crear(@Valid @RequestBody AlimentoRequest peticion,
            @Parameter(description = "Origen de los datos (obligatorio, para la trazabilidad)", example = "BEDCA")
            @RequestParam FuenteDato fuente,
            @Parameter(description = "Identificador o URL en la fuente", example = "BEDCA-0123")
            @RequestParam(required = false) String referenciaExterna) {
        AlimentoResponse creado = adminCatalogoServicio.crearAlimento(peticion, fuente, referenciaExterna);
        return ResponseEntity.created(URI.create("/api/v1/alimentos/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar cualquier alimento",
            description = "También los privados de usuarios. Si cambian los alérgenos o la lactosa, vuelven a "
                    + "quedar sin revisar.")
    @ApiResponse(responseCode = "200", description = "Alimento actualizado")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public AlimentoResponse editar(@PathVariable UUID id, @Valid @RequestBody AlimentoRequest peticion) {
        return adminCatalogoServicio.editarAlimento(id, peticion);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un alimento",
            description = "Si ya se ha usado en comidas, solo se desactiva (el historial lo conserva).")
    @ApiResponse(responseCode = "204", description = "Alimento eliminado o desactivado")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        adminCatalogoServicio.eliminarAlimento(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importar alimentos desde CSV",
            description = "Alta o actualización por nombre, marca y preparación, con los alérgenos sin revisar. "
                    + "Máximo 5000 filas y 10 MB. Las filas con errores no se importan y se devuelven en el "
                    + "resultado; un fichero sin cabecera o sin alguna columna responde 400.")
    @ApiResponse(responseCode = "200", description = "Resultado: alimentos creados, actualizados y filas con errores")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "413", ref = RespuestasApi.ARCHIVO_DEMASIADO_GRANDE)
    public ResultadoImportacion importar(
            @Parameter(description = "Fichero CSV en UTF-8")
            @RequestPart("archivo") MultipartFile archivo,
            @Parameter(description = "Origen de los datos de todo el fichero", example = "BEDCA")
            @RequestParam FuenteDato fuente,
            @Parameter(description = "Referencia común (versión o fecha de la fuente)", example = "BEDCA 2024")
            @RequestParam(required = false) String referencia) {
        return adminCatalogoServicio.importarCsv(archivo, fuente, referencia);
    }

    @PutMapping("/{id}/alergenos")
    @Operation(summary = "Revisar los alérgenos de un alimento",
            description = "Sustituye los alérgenos que contiene, las trazas y la lactosa, y lo marca como "
                    + "revisado (deja de excluirse para los usuarios con alergias estrictas).")
    @ApiResponse(responseCode = "200", description = "Alérgenos revisados")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public AlimentoResponse revisarAlergenos(@PathVariable UUID id,
            @Valid @RequestBody RevisionAlergenosRequest peticion) {
        return adminCatalogoServicio.revisarAlergenos(id, peticion);
    }
}
