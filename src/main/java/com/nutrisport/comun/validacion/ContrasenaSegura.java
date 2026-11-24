package com.nutrisport.comun.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Contraseña aceptable (§8.1): entre 10 y 72 caracteres, al menos una letra y un número, y que no
 * figure en la lista de contraseñas comunes. Un valor nulo se considera válido: combínala con
 * {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = ContrasenaSeguraValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ContrasenaSegura {

    String message() default "La contraseña no es suficientemente segura";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
