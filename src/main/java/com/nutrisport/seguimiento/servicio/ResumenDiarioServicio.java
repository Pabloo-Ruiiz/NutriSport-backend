package com.nutrisport.seguimiento.servicio;

import com.nutrisport.actividad.dto.EntrenamientoResponse;
import com.nutrisport.actividad.servicio.EntrenamientoServicio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.comidas.dto.ComidasDiaResponse;
import com.nutrisport.comidas.servicio.ComidaServicio;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.menus.servicio.MenuServicio;
import com.nutrisport.perfil.dto.ObjetivoDiarioResponse;
import com.nutrisport.perfil.servicio.ObjetivoDiarioServicio;
import com.nutrisport.seguimiento.dto.ResumenDiarioResponse;
import com.nutrisport.seguimiento.dto.ResumenDiarioResponse.Porcentajes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resumen diario del usuario autenticado (§3.F): consumido frente al objetivo de ese día, guardado en
 * ObjetivoDiario para que no cambie al actualizar el perfil. Si el día aún no tiene objetivo, se crea con
 * el perfil actual (sin perfil, 422 PERFIL_REQUERIDO). Incluye las comidas planificadas de ese día aún no consumidas
 * en los menús ACTIVO.
 */
@Service
@Transactional
public class ResumenDiarioServicio {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final ObjetivoDiarioServicio objetivoDiarioServicio;
    private final ComidaServicio comidaServicio;
    private final EntrenamientoServicio entrenamientoServicio;
    private final MenuServicio menuServicio;
    private final SeguridadUtils seguridadUtils;
    private final Clock reloj;

    public ResumenDiarioServicio(ObjetivoDiarioServicio objetivoDiarioServicio, ComidaServicio comidaServicio,
            EntrenamientoServicio entrenamientoServicio, MenuServicio menuServicio, SeguridadUtils seguridadUtils,
            Clock reloj) {
        this.objetivoDiarioServicio = objetivoDiarioServicio;
        this.comidaServicio = comidaServicio;
        this.entrenamientoServicio = entrenamientoServicio;
        this.menuServicio = menuServicio;
        this.seguridadUtils = seguridadUtils;
        this.reloj = reloj;
    }

    /** Resumen del día indicado o, si es nulo, de hoy (en la zona horaria de la aplicación). */
    public ResumenDiarioResponse de(LocalDate fecha) {
        LocalDate dia = fecha != null ? fecha : LocalDate.now(reloj);
        UUID usuarioId = seguridadUtils.usuarioActualId();
        ObjetivoDiarioResponse objetivoDiario = objetivoDiarioServicio.obtener(usuarioId, dia);
        ComidasDiaResponse comidas = comidaServicio.dia(dia);
        List<EntrenamientoResponse> entrenamientos = entrenamientoServicio.listar(dia, dia, null);

        Nutrientes objetivo = objetivoDiario.total();
        Nutrientes consumido = comidas.totales();
        Nutrientes restante = new Nutrientes(
                restante(objetivo.kcal(), consumido.kcal()),
                restante(objetivo.proteina(), consumido.proteina()),
                restante(objetivo.carbohidratos(), consumido.carbohidratos()),
                restante(objetivo.grasa(), consumido.grasa()));
        BigDecimal exceso = consumido.kcal().subtract(objetivo.kcal());
        Porcentajes porcentajes = new Porcentajes(
                porcentaje(consumido.kcal(), objetivo.kcal()),
                porcentaje(consumido.proteina(), objetivo.proteina()),
                porcentaje(consumido.carbohidratos(), objetivo.carbohidratos()),
                porcentaje(consumido.grasa(), objetivo.grasa()));

        return new ResumenDiarioResponse(dia, objetivo, objetivoDiario.kcalEntreno(), consumido, restante,
                exceso.signum() > 0 ? exceso : null, porcentajes, comidas.comidas(), entrenamientos,
                menuServicio.pendientesDelDia(usuarioId, dia));
    }

    private static BigDecimal restante(BigDecimal objetivo, BigDecimal consumido) {
        return objetivo.subtract(consumido).max(BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP);
    }

    /** consumido / objetivo × 100 redondeado a entero; 0 si el objetivo es 0. */
    private static int porcentaje(BigDecimal consumido, BigDecimal objetivo) {
        if (objetivo.signum() == 0) {
            return 0;
        }
        return consumido.multiply(CIEN).divide(objetivo, 0, RoundingMode.HALF_UP).intValueExact();
    }
}
