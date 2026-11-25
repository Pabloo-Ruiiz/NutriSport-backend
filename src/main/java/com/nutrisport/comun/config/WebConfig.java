package com.nutrisport.comun.config;

import com.nutrisport.seguimiento.entidad.Agrupacion;
import java.util.Locale;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Configuración de Spring MVC. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Rutas de las funciones que tratan datos de salud (§8.3). */
    static final String[] RUTAS_DATOS_SALUD = {
        "/api/v1/perfil/**",
        "/api/v1/comidas/**",
        "/api/v1/analisis/**",
        "/api/v1/entrenamientos/**",
        "/api/v1/seguimiento/**",
        "/api/v1/menus/**",
        "/api/v1/asistente/**"
    };

    private final InterceptorConsentimiento interceptorConsentimiento;

    public WebConfig(InterceptorConsentimiento interceptorConsentimiento) {
        this.interceptorConsentimiento = interceptorConsentimiento;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(interceptorConsentimiento).addPathPatterns(RUTAS_DATOS_SALUD);
    }

    /** {@code ?agrupacion=semana} y {@code ?periodo=mes} también en minúsculas, como los escribe §7.7. */
    @Override
    public void addFormatters(FormatterRegistry registro) {
        registro.addConverter(String.class, Agrupacion.class,
                valor -> Agrupacion.valueOf(valor.strip().toUpperCase(Locale.ROOT)));
    }
}
