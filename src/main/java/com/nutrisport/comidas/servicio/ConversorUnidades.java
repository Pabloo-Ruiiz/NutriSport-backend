package com.nutrisport.comidas.servicio;

import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.PorcionCasera;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.util.TextoNormalizado;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Convierte la cantidad que introduce el usuario a gramos (o mililitros, si el alimento se mide en ML),
 * que es la base del cálculo nutricional (§3.C):
 * <ul>
 * <li>{@code g} → la cantidad; {@code kg} → × 1000.</li>
 * <li>{@code ml} y {@code l} (× 1000) solo para alimentos con unidad base ML, con densidad 1 (simplificación).</li>
 * <li>Cualquier otra unidad debe ser el nombre de una porción casera del alimento, sin distinguir
 * mayúsculas ni tildes: cantidad × gramos de la porción.</li>
 * </ul>
 * El resultado lleva un decimal, como la columna {@code alimento_registrado.gramos}.
 */
@Component
public class ConversorUnidades {

    private static final BigDecimal MIL = new BigDecimal("1000");

    /** @throws PeticionInvalidaException con el campo {@code unidad} si la unidad no se puede convertir */
    public BigDecimal aGramos(Alimento alimento, BigDecimal cantidad, String unidad) {
        String normalizada = TextoNormalizado.normalizar(unidad);
        boolean liquido = alimento.getUnidadBase() == UnidadBase.ML;
        BigDecimal gramos = switch (normalizada == null ? "" : normalizada) {
            case "g" -> cantidad;
            case "kg" -> cantidad.multiply(MIL);
            case "ml" -> liquido ? cantidad : null;
            case "l" -> liquido ? cantidad.multiply(MIL) : null;
            default -> porcion(alimento, normalizada)
                    .map(porcion -> cantidad.multiply(porcion.gramos()))
                    .orElse(null);
        };
        if (gramos == null) {
            throw new PeticionInvalidaException("La petición contiene datos no válidos",
                    List.of(new ErrorCampo("unidad", mensajeError(alimento, unidad, liquido))));
        }
        return gramos.setScale(1, RoundingMode.HALF_UP);
    }

    private static Optional<PorcionCasera> porcion(Alimento alimento, String unidadNormalizada) {
        return alimento.getPorciones().stream()
                .filter(porcion -> TextoNormalizado.normalizar(porcion.nombre()).equals(unidadNormalizada))
                .findFirst();
    }

    private static String mensajeError(Alimento alimento, String unidad, boolean liquido) {
        List<String> admitidas = new ArrayList<>(liquido ? List.of("g", "kg", "ml", "l") : List.of("g", "kg"));
        alimento.getPorciones().forEach(porcion -> admitidas.add(porcion.nombre()));
        return "La unidad '" + unidad + "' no es válida para " + alimento.getNombre() + ". Unidades admitidas: "
                + String.join(", ", admitidas);
    }
}
