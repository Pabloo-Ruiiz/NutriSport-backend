package com.nutrisport.comun.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Fecha de nacimiento de una persona con al menos 18 años cumplidos hoy (en la zona horaria de la
 * aplicación). Un valor nulo se considera válido: combínala con {@code @NotNull}.
 */
@Documented
@Constraint(validatedBy = MayorDeEdadValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MayorDeEdad {

    String message() default "Debes tener al menos 18 años";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
