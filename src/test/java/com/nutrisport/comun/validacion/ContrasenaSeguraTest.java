package com.nutrisport.comun.validacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutrisport.IntegracionBase;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Usa el validador de Spring para comprobar también que la lista se inyecta en el validador. */
class ContrasenaSeguraTest extends IntegracionBase {

    record Peticion(@ContrasenaSegura String password) {
    }

    private final Validator validator;
    private final ContrasenasComunes contrasenasComunes;

    @Autowired
    ContrasenaSeguraTest(Validator validator, ContrasenasComunes contrasenasComunes) {
        this.validator = validator;
        this.contrasenasComunes = contrasenasComunes;
    }

    @Test
    void cargaLaListaDeContrasenasComunes() {
        assertThat(contrasenasComunes.tamano()).isGreaterThanOrEqualTo(200);
        assertThat(contrasenasComunes.contiene("Password123")).isTrue();
    }

    @Test
    void aceptaContrasenasSegurasYNulas() {
        assertThat(errores("avena-y-platano-42")).isEmpty();
        assertThat(errores("Correr10KmCadaDía")).isEmpty();
        assertThat(errores(null)).isEmpty();
    }

    @Test
    void rechazaLongitudFueraDeRango() {
        assertThat(errores("corta1")).containsExactly("Debe tener entre 10 y 72 caracteres");
        assertThat(errores("a1".repeat(37))).containsExactly("Debe tener entre 10 y 72 caracteres");
        // 72 caracteres pero más de 72 bytes en UTF-8: BCrypt ignoraría el final
        assertThat(errores("ñ".repeat(71) + "1")).containsExactly("Debe tener entre 10 y 72 caracteres");
    }

    @Test
    void exigeUnaLetraYUnNumero() {
        assertThat(errores("solamenteletras")).containsExactly("Debe contener al menos una letra y un número");
        assertThat(errores("1234567890123")).containsExactly("Debe contener al menos una letra y un número");
    }

    @Test
    void rechazaContrasenasComunesSinDistinguirMayusculas() {
        assertThat(errores("Password123")).containsExactly("Es una contraseña demasiado común");
        assertThat(errores("BARCELONA10")).containsExactly("Es una contraseña demasiado común");
        assertThat(errores("Contraseña123")).containsExactly("Es una contraseña demasiado común");
    }

    private Set<String> errores(String password) {
        return validator.validate(new Peticion(password)).stream()
                .map(ConstraintViolation::getMessage)
                .collect(java.util.stream.Collectors.toSet());
    }
}
