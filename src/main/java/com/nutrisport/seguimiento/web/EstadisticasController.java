package com.nutrisport.seguimiento.web;

import com.nutrisport.comun.documentacion.RespuestasApi;
import com.nutrisport.seguimiento.dto.ComparativaResponse;
import com.nutrisport.seguimiento.dto.EstadisticasResponse;
import com.nutrisport.seguimiento.entidad.Agrupacion;
import com.nutrisport.seguimiento.servicio.ComparativaServicio;
import com.nutrisport.seguimiento.servicio.EstadisticasServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estadísticas y comparativas del deportista autenticado (§3.F y §7.7, Ampliación). Son datos de salud: con el
 * consentimiento revocado responden 403 CONSENTIMIENTO_REVOCADO (WebConfig).
 */
@RestController
@RequestMapping("/api/v1/seguimiento")
@PreAuthorize("hasRole('DEPORTISTA')")
@Tag(name = "Seguimiento")
@SecurityRequirement(name = RespuestasApi.SEGURIDAD)
@ApiResponse(responseCode = "401", ref = RespuestasApi.NO_AUTENTICADO)
@ApiResponse(responseCode = "403", ref = RespuestasApi.SIN_PERMISO)
public class EstadisticasController {

    private final EstadisticasServicio estadisticasServicio;
    private final ComparativaServicio comparativaServicio;

    public EstadisticasController(EstadisticasServicio estadisticasServicio, ComparativaServicio comparativaServicio) {
        this.estadisticasServicio = estadisticasServicio;
        this.comparativaServicio = comparativaServicio;
    }

    @GetMapping("/estadisticas")
    @Operation(summary = "Estadísticas de un periodo",
            description = "Serie de energía y macros consumidos frente al objetivo guardado de cada día, agrupada por "
                    + "día, semana ISO o mes; media diaria y objetivo medio (sobre los días con alguna comida); días "
                    + "en objetivo (energía en ±10 %) y adherencia; reparto de macros de la media diaria (suma 100); "
                    + "kcal medias por tipo de comida y los 10 alimentos más consumidos. Si un día con comidas no "
                    + "tiene objetivo, se crea con el perfil actual. Rango de como mucho 366 días.")
    @ApiResponse(responseCode = "200", description = "Estadísticas del periodo")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public EstadisticasResponse estadisticas(
            @Parameter(description = "Primer día (incluido)", example = "2026-10-26")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Último día (incluido); como mucho 366 días contando desde", example = "2026-11-22")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(description = "DIA (por defecto), SEMANA o MES; también en minúsculas", example = "semana")
            @RequestParam(required = false) Agrupacion agrupacion) {
        return estadisticasServicio.calcular(desde, hasta, agrupacion);
    }

    @GetMapping("/comparativa")
    @Operation(summary = "Periodo actual frente al anterior",
            description = "SEMANA (por defecto): de lunes a hoy frente a la semana anterior completa. MES: del día 1 "
                    + "a hoy frente al mes anterior completo. Estadísticas resumidas (sin serie) y variación de la "
                    + "media diaria en % (nula si el periodo anterior no tiene datos) y de la adherencia en puntos.")
    @ApiResponse(responseCode = "200", description = "Comparativa")
    @ApiResponse(responseCode = "400", ref = RespuestasApi.VALIDACION)
    public ComparativaResponse comparativa(
            @Parameter(description = "SEMANA o MES; también en minúsculas", example = "semana")
            @RequestParam(required = false) Agrupacion periodo) {
        return comparativaServicio.comparar(periodo);
    }
}
