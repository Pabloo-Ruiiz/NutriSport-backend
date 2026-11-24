package com.nutrisport.comun.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de la documentación OpenAPI y de Swagger UI.
 * Declara el esquema de seguridad "bearerAuth" y lo aplica a todas las operaciones
 * para que Swagger UI muestre el botón Authorize.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_SEGURIDAD = "bearerAuth";

    @Bean
    public OpenAPI nutriSportOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("NutriSport AI API")
                        .version("v1")
                        .description("API REST de NutriSport AI para que los deportistas registren y analicen "
                                + "su alimentación, sus entrenamientos y su progreso."))
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_SEGURIDAD, new SecurityScheme()
                                .name(ESQUEMA_SEGURIDAD)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_SEGURIDAD));
    }
}
