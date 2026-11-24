package com.nutrisport.perfil.web;

import com.nutrisport.perfil.dto.ObjetivosManualesRequest;
import com.nutrisport.perfil.dto.ObjetivosResponse;
import com.nutrisport.perfil.dto.PerfilRequest;
import com.nutrisport.perfil.dto.PerfilResponse;
import com.nutrisport.perfil.dto.PreferenciaDto;
import com.nutrisport.perfil.dto.PreferenciasRequest;
import com.nutrisport.perfil.servicio.PerfilServicio;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Perfil", description = "Perfil deportivo, objetivos, preferencias y restricciones alimentarias")
public class PerfilController {

    private final PerfilServicio perfilServicio;

    public PerfilController(PerfilServicio perfilServicio) {
        this.perfilServicio = perfilServicio;
    }

    @GetMapping
    @Operation(summary = "Obtener el perfil deportivo", description = "404 PERFIL_NO_ENCONTRADO si aún no existe.")
    public PerfilResponse obtener() {
        return perfilServicio.obtener();
    }

    @PutMapping
    @Operation(summary = "Crear o actualizar el perfil deportivo",
            description = "fechaCompeticion es obligatoria si el objetivo es COMPETICION.")
    public PerfilResponse guardar(@Valid @RequestBody PerfilRequest peticion) {
        return perfilServicio.guardar(peticion);
    }

    @GetMapping("/objetivos")
    @Operation(summary = "Objetivos base y desglose del cálculo",
            description = "Objetivos vigentes (sin entrenamiento), si son manuales, desglose del cálculo automático "
                    + "y avisos de seguridad. 404 PERFIL_NO_ENCONTRADO si aún no hay perfil.")
    public ObjetivosResponse obtenerObjetivos() {
        return perfilServicio.obtenerObjetivos();
    }

    @PutMapping("/objetivos")
    @Operation(summary = "Fijar objetivos manuales",
            description = "Dejan de recalcularse al cambiar el perfil. Devuelve avisos (no bloquea) si la energía "
                    + "de los macros se aleja más de un 10 % de kcal o si kcal está bajo el mínimo de seguridad.")
    public ObjetivosResponse fijarObjetivosManuales(@Valid @RequestBody ObjetivosManualesRequest peticion) {
        return perfilServicio.fijarObjetivosManuales(peticion);
    }

    @DeleteMapping("/objetivos/manuales")
    @Operation(summary = "Volver al cálculo automático", description = "Recalcula los objetivos con el perfil actual.")
    public ObjetivosResponse volverACalculoAutomatico() {
        return perfilServicio.volverACalculoAutomatico();
    }

    @GetMapping("/preferencias")
    @Operation(summary = "Listar preferencias y restricciones alimentarias")
    public List<PreferenciaDto> obtenerPreferencias() {
        return perfilServicio.obtenerPreferencias();
    }

    @PutMapping("/preferencias")
    @Operation(summary = "Reemplazar preferencias y restricciones",
            description = "Sustituye la lista completa (máximo 100). Una lista vacía las borra todas.")
    public List<PreferenciaDto> reemplazarPreferencias(@Valid @RequestBody PreferenciasRequest peticion) {
        return perfilServicio.reemplazarPreferencias(peticion);
    }
}
