package com.nutrisport.comun.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/**
 * Valida {@link ContrasenaSegura}. Spring lo crea con su fábrica de validadores e inyecta la lista
 * de contraseñas comunes por constructor. Devuelve un mensaje concreto para cada regla incumplida.
 */
public class ContrasenaSeguraValidator implements ConstraintValidator<ContrasenaSegura, String> {

    static final int LONGITUD_MINIMA = 10;
    static final int LONGITUD_MAXIMA = 72;

    private final ContrasenasComunes contrasenasComunes;

    public ContrasenaSeguraValidator(ContrasenasComunes contrasenasComunes) {
        this.contrasenasComunes = contrasenasComunes;
    }

    @Override
    public boolean isValid(String contrasena, ConstraintValidatorContext contexto) {
        if (contrasena == null) {
            return true;
        }
        int longitud = contrasena.codePointCount(0, contrasena.length());
        // BCrypt solo usa los primeros 72 bytes: con tildes o eñes 72 caracteres pueden ser más bytes
        if (longitud < LONGITUD_MINIMA || longitud > LONGITUD_MAXIMA
                || contrasena.getBytes(StandardCharsets.UTF_8).length > LONGITUD_MAXIMA) {
            return rechazar(contexto, "Debe tener entre " + LONGITUD_MINIMA + " y " + LONGITUD_MAXIMA + " caracteres");
        }
        if (contrasena.codePoints().noneMatch(Character::isLetter)
                || contrasena.codePoints().noneMatch(Character::isDigit)) {
            return rechazar(contexto, "Debe contener al menos una letra y un número");
        }
        if (contrasenasComunes.contiene(contrasena)) {
            return rechazar(contexto, "Es una contraseña demasiado común");
        }
        return true;
    }

    private static boolean rechazar(ConstraintValidatorContext contexto, String mensaje) {
        contexto.disableDefaultConstraintViolation();
        contexto.buildConstraintViolationWithTemplate(mensaje).addConstraintViolation();
        return false;
    }
}
