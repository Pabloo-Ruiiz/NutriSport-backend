package com.nutrisport.comun.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicación en la zona horaria {@code app.zona-horaria}: "hoy" es el día del usuario y
 * no el del servidor. Inyectarlo en lugar de llamar a {@code LocalDate.now()} permite fijar la fecha
 * en los tests.
 */
@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj(@Value("${app.zona-horaria}") String zonaHoraria) {
        return Clock.system(ZoneId.of(zonaHoraria));
    }
}
