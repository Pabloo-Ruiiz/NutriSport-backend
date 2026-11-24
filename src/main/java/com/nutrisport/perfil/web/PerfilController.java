package com.nutrisport.perfil.web;

import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.perfil.dto.ObjetivosManualesRequest;
import com.nutrisport.perfil.dto.ObjetivosResponse;
import com.nutrisport.perfil.dto.PerfilRequest;
import com.nutrisport.perfil.dto.PerfilResponse;
import com.nutrisport.perfil.dto.PreferenciaDto;
import com.nutrisport.perfil.dto.PreferenciasRequest;
import com.nutrisport.perfil.servicio.PerfilServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perfil deportivo y preferencias alimentarias del deportista autenticado (§7.2). Son datos de salud:
 * con el consentimiento revocado responden 403 CONSENTIMIENTO_REVOCADO (WebConfig).
 */
@RestController
@RequestMapping("/api/v1/perfil")
@PreAuthorize("hasRole('DEPORTISTA')")
@Tag(name = "Perfil", description = "Perfil deportivo, objetivos nutricionales (calculados o manuales) y "
        + "preferencias y restricciones alimentarias. Son datos de salud: requieren el consentimiento vigente.")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
@ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
public class PerfilController {

    private final PerfilServicio perfilServicio;

    public PerfilController(PerfilServicio perfilServicio) {
        this.perfilServicio = perfilServicio;
    }

    @GetMapping
    @Operation(summary = "Obtener el perfil deportivo",
            description = "404 PERFIL_NO_ENCONTRADO si aún no se ha creado.")
    @ApiResponse(responseCode = "200", description = "El perfil")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public PerfilResponse obtener() {
        return perfilServicio.obtener();
    }

    @PutMapping
    @Operation(summary = "Crear o actualizar el perfil deportivo",
            description = "Edad mínima de 18 años, altura 120–230 cm y peso 30–250 kg. deportePrincipal es un código "
                    + "de GET /entrenamientos/tipos. fechaCompeticion es obligatoria si el objetivo es COMPETICION. "
                    + "Si los objetivos no son manuales, se recalculan.")
    @ApiResponse(responseCode = "200", description = "Perfil guardado")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public PerfilResponse guardar(@Valid @RequestBody PerfilRequest peticion) {
        return perfilServicio.guardar(peticion);
    }

    @GetMapping("/objetivos")
    @Operation(summary = "Objetivos base y desglose del cálculo",
            description = "Objetivos vigentes sin contar el entrenamiento, si son manuales, desglose del cálculo "
                    + "automático (metabolismo basal, factor de actividad y ajuste por objetivo) y avisos de seguridad.")
    @ApiResponse(responseCode = "200", description = "Objetivos y desglose")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ObjetivosResponse obtenerObjetivos() {
        return perfilServicio.obtenerObjetivos();
    }

    @PutMapping("/objetivos")
    @Operation(summary = "Fijar objetivos manuales",
            description = "Dejan de recalcularse al cambiar el perfil. Devuelve avisos (no bloquea) si la energía "
                    + "de los macros se aleja más de un 10 % de kcal o si kcal está bajo el mínimo de seguridad. "
                    + "Recalcula los objetivos diarios desde hoy.")
    @ApiResponse(responseCode = "200", description = "Objetivos manuales guardados, con avisos si los hay")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ObjetivosResponse fijarObjetivosManuales(@Valid @RequestBody ObjetivosManualesRequest peticion) {
        return perfilServicio.fijarObjetivosManuales(peticion);
    }

    @DeleteMapping("/objetivos/manuales")
    @Operation(summary = "Volver al cálculo automático",
            description = "Recalcula los objetivos con el perfil actual y los objetivos diarios desde hoy.")
    @ApiResponse(responseCode = "200", description = "Objetivos calculados")
    @ApiResponse(responseCode = "404", ref = RespuestasApi.NO_ENCONTRADO)
    public ObjetivosResponse volverACalculoAutomatico() {
        return perfilServicio.volverACalculoAutomatico();
    }

    @GetMapping("/preferencias")
    @Operation(summary = "Listar preferencias y restricciones alimentarias",
            description = "Alergias, intolerancias, dietas y alimentos o categorías rechazados, con su severidad.")
    @ApiResponse(responseCode = "200", description = "Preferencias del usuario")
    public List<PreferenciaDto> obtenerPreferencias() {
        return perfilServicio.obtenerPreferencias();
    }

    @PutMapping("/preferencias")
    @Operation(summary = "Reemplazar preferencias y restricciones",
            description = "Sustituye la lista completa (máximo 100). Una lista vacía las borra todas. Cada tipo "
                    + "exige su campo: alergeno (ALERGIA), intolerancia (INTOLERANCIA), dieta (DIETA) o "
                    + "alimentoId o categoriaId (RECHAZO y FAVORITO). ESTRICTA genera avisos al registrar comidas.")
    @ApiResponse(responseCode = "200", description = "Preferencias guardadas")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public List<PreferenciaDto> reemplazarPreferencias(@Valid @RequestBody PreferenciasRequest peticion) {
        return perfilServicio.reemplazarPreferencias(peticion);
    }
}
