package com.nutrisport.catalogo.dto;

import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.PorcionCasera;
import com.nutrisport.catalogo.entidad.Preparacion;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.perfil.entidad.Alergeno;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Detalle de un alimento (GET /api/v1/alimentos/{id}, §7.3), con su fuente para la trazabilidad. Los
 * nutrientes secundarios nulos significan "dato no disponible". {@code propio} indica si es un
 * alimento privado del usuario autenticado (y por tanto puede editarlo o borrarlo).
 */
public record AlimentoResponse(
        UUID id,
        String nombre,
        String marca,
        UUID categoriaId,
        String categoriaNombre,
        Preparacion preparacion,
        BigDecimal kcal100,
        BigDecimal proteinas100,
        BigDecimal carbohidratos100,
        BigDecimal grasas100,
        BigDecimal azucares100,
        BigDecimal saturadas100,
        BigDecimal fibra100,
        BigDecimal sal100,
        UnidadBase unidadBase,
        List<PorcionCasera> porciones,
        List<Alergeno> alergenosContiene,
        List<Alergeno> alergenosTrazas,
        boolean alergenosRevisados,
        boolean contieneLactosa,
        boolean aptoVegano,
        boolean aptoVegetariano,
        boolean aptoPescetariano,
        FuenteDato fuente,
        String referenciaExterna,
        Visibilidad visibilidad,
        boolean validado,
        boolean activo,
        boolean propio) {
}
