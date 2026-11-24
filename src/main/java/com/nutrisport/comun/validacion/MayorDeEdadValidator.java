package com.nutrisport.comun.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Valida {@link MayorDeEdad}. Spring lo crea con su fábrica de validadores e inyecta el reloj de la
 * aplicación (RelojConfig), para que "hoy" sea el día del usuario y no el del servidor. Quien nació un
 * 29 de febrero cumple los años el 28 de febrero en los años no bisiestos.
 */
public class MayorDeEdadValidator implements ConstraintValidator<MayorDeEdad, LocalDate> {

    static final int EDAD_MINIMA = 18;

    private final Clock reloj;

    public MayorDeEdadValidator(Clock reloj) {
        this.reloj = reloj;
    }

    @Override
    public boolean isValid(LocalDate fechaNacimiento, ConstraintValidatorContext contexto) {
        if (fechaNacimiento == null) {
            return true;
        }
        LocalDate hoy = LocalDate.now(reloj);
        return !fechaNacimiento.plusYears(EDAD_MINIMA).isAfter(hoy);
    }
}
