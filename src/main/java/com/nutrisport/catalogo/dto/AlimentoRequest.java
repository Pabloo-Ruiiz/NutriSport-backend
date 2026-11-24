package com.nutrisport.catalogo.dto;

import com.nutrisport.catalogo.entidad.PorcionCasera;
import com.nutrisport.catalogo.entidad.Preparacion;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.perfil.entidad.Alergeno;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Crea o edita un alimento privado propio (POST y PUT /api/v1/alimentos, §7.3). Valores por 100 g o
 * 100 ml según {@code unidadBase}. Los nutrientes secundarios son opcionales: si no se conocen se
 * dejan nulos, nunca a 0. Si no se indica la preparación, se guarda {@code OTRA}.
 */
public record AlimentoRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String nombre,

        @Size(max = 80, message = "La marca no puede superar los 80 caracteres")
        String marca,

        @NotNull(message = "La categoría es obligatoria")
        UUID categoriaId,

        Preparacion preparacion,

        @NotNull(message = "Las kcal por 100 son obligatorias")
        @DecimalMin(value = "0", message = "Las kcal deben estar entre 0 y 900")
        @DecimalMax(value = "900", message = "Las kcal deben estar entre 0 y 900")
        @Digits(integer = 3, fraction = 2, message = "Admite como mucho dos decimales")
        BigDecimal kcal100,

        @NotNull(message = "Las proteínas por 100 son obligatorias")
        @DecimalMin(value = "0", message = "Debe estar entre 0 y 100")
        @DecimalMax(value = "100", message = "Debe estar entre 0 y 100")
        @Digits(integer = 3, fraction = 2, message = "Admite como mucho dos decimales")
        BigDecimal proteinas100,

        @NotNull(message = "Los carbohidratos por 100 son obligatorios")
        @DecimalMin(value = "0", message = "Debe estar entre 0 y 100")
        @DecimalMax(value = "100", message = "Debe estar entre 0 y 100")
        @Digits(integer = 3, fraction = 2, message = "Admite como mucho dos decimales")
        BigDecimal carbohidratos100,

        @NotNull(message = "Las grasas por 100 son obligatorias")
        @DecimalMin(value = "0", message = "Debe estar entre 0 y 100")
        @DecimalMax(value = "100", message = "Debe estar entre 0 y 100")
        @Digits(integer = 3, fraction = 2, message = "Admite como mucho dos decimales")
        BigDecimal grasas100,

        @DecimalMin(value = "0", message = "Debe estar entre 0 y 100")
        @DecimalMax(value = "100", message = "Debe estar entre 0 y 100")
        @Digits(integer = 3, fraction = 2, message = "Admite como mucho dos decimales")
        BigDecimal azucares100,

        @DecimalMin(value = "0", message = "Debe estar entre 0 y 100")
        @DecimalMax(value = "100", message = "Debe estar entre 0 y 100")
        @Digits(integer = 3, fraction = 2, message = "Admite como mucho dos decimales")
        BigDecimal saturadas100,

        @DecimalMin(value = "0", message = "Debe estar entre 0 y 100")
        @DecimalMax(value = "100", message = "Debe estar entre 0 y 100")
        @Digits(integer = 3, fraction = 2, message = "Admite como mucho dos decimales")
        BigDecimal fibra100,

        @DecimalMin(value = "0", message = "Debe estar entre 0 y 100")
        @DecimalMax(value = "100", message = "Debe estar entre 0 y 100")
        @Digits(integer = 3, fraction = 2, message = "Admite como mucho dos decimales")
        BigDecimal sal100,

        @NotNull(message = "La unidad base es obligatoria")
        UnidadBase unidadBase,

        @Size(max = 20, message = "No se admiten más de 20 porciones")
        List<@Valid @NotNull(message = "La porción no puede ser nula") PorcionCasera> porciones,

        Set<@NotNull(message = "El alérgeno no puede ser nulo") Alergeno> alergenosContiene,

        Set<@NotNull(message = "El alérgeno no puede ser nulo") Alergeno> alergenosTrazas,

        @NotNull(message = "Indica si contiene lactosa")
        Boolean contieneLactosa,

        @NotNull(message = "Indica si es apto para veganos")
        Boolean aptoVegano,

        @NotNull(message = "Indica si es apto para vegetarianos")
        Boolean aptoVegetariano,

        @NotNull(message = "Indica si es apto para pescetarianos")
        Boolean aptoPescetariano) {
}
