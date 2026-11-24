package com.nutrisport.comun.config;

import com.nutrisport.auth.seguridad.FiltroJwt;
import com.nutrisport.auth.seguridad.JwtServicio;
import com.nutrisport.auth.seguridad.ManejadorAccesoDenegado;
import com.nutrisport.auth.seguridad.PuntoEntradaNoAutenticado;
import com.nutrisport.auth.seguridad.UsuarioDetailsServiceImpl;
import com.nutrisport.catalogo.servicio.ProductoServicio;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Seguridad de la API (§5.5, §8.1 y §8.2): sin estado, autenticación con JWT en la cabecera
 * {@code Authorization}, RBAC con {@code @PreAuthorize} y errores 401/403 en formato Problem Details.
 * <p>
 * Hay dos cadenas de filtros:
 * <ol>
 * <li>Documentación (Swagger UI y OpenAPI): pública y con una CSP que permite cargar la interfaz.
 * Solo está activa en los entornos en los que springdoc está habilitado (perfil local).</li>
 * <li>API: el resto de rutas, con la CSP estricta de una API JSON.</li>
 * </ol>
 */
@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

    /** BCrypt con coste 12 (§8.1). */
    private static final int COSTE_BCRYPT = 12;

    private static final String[] RUTAS_DOCUMENTACION = {"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"};

    private static final String[] RUTAS_AUTH_PUBLICAS = {
        "/api/v1/auth/registro",
        "/api/v1/auth/verificar-email",
        "/api/v1/auth/reenviar-verificacion",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        // Público: funciona solo con la cookie del refresh token
        "/api/v1/auth/logout",
        "/api/v1/auth/recuperar-password",
        "/api/v1/auth/restablecer-password"
    };

    /** Una API JSON no carga recursos ni se muestra en marcos. */
    private static final String CSP_API = "default-src 'none'; frame-ancestors 'none'";

    /**
     * Swagger UI carga sus scripts y estilos del propio servidor, usa estilos en línea (React)
     * e iconos {@code data:}, y pide el documento OpenAPI con fetch.
     */
    private static final String CSP_DOCUMENTACION = "default-src 'none'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'";

    private static final long UN_ANIO_EN_SEGUNDOS = 31_536_000L;

    private final JwtServicio jwtServicio;
    private final UsuarioDetailsServiceImpl usuarioDetailsService;
    private final PuntoEntradaNoAutenticado puntoEntradaNoAutenticado;
    private final ManejadorAccesoDenegado manejadorAccesoDenegado;
    private final String urlFrontend;

    public SeguridadConfig(JwtServicio jwtServicio, UsuarioDetailsServiceImpl usuarioDetailsService,
            PuntoEntradaNoAutenticado puntoEntradaNoAutenticado, ManejadorAccesoDenegado manejadorAccesoDenegado,
            @Value("${app.frontend-url}") String urlFrontend) {
        this.jwtServicio = jwtServicio;
        this.usuarioDetailsService = usuarioDetailsService;
        this.puntoEntradaNoAutenticado = puntoEntradaNoAutenticado;
        this.manejadorAccesoDenegado = manejadorAccesoDenegado;
        this.urlFrontend = urlFrontend;
    }

    /** Cadena de la documentación: pública, sin JWT y con la CSP que necesita Swagger UI. */
    @Bean
    @Order(1)
    public SecurityFilterChain cadenaDocumentacion(HttpSecurity http) throws Exception {
        http.securityMatcher(RUTAS_DOCUMENTACION);
        configurarComun(http, CSP_DOCUMENTACION);
        http.authorizeHttpRequests(peticiones -> peticiones.anyRequest().permitAll());
        return http.build();
    }

    /** Cadena de la API. */
    @Bean
    @Order(2)
    public SecurityFilterChain cadenaApi(HttpSecurity http) throws Exception {
        configurarComun(http, CSP_API);
        http
                .cors(cors -> cors.configurationSource(origenesPermitidos()))
                // El filtro no es un bean para que Spring Boot no lo registre también como filtro de servlet
                .addFilterBefore(new FiltroJwt(jwtServicio, usuarioDetailsService),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(puntoEntradaNoAutenticado)
                        .accessDeniedHandler(manejadorAccesoDenegado))
                .authorizeHttpRequests(peticiones -> peticiones
                        // Respuestas asíncronas (streaming del asistente) y página de error: la petición
                        // original ya pasó por la autorización
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, RUTAS_AUTH_PUBLICAS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated());
        return http.build();
    }

    /** Configuración compartida por las dos cadenas. */
    private static void configurarComun(HttpSecurity http, String politicaContenido) throws Exception {
        http
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Sin sesión ni cookies de autenticación en las peticiones de la API: CSRF no aplica.
                // /auth/refresh (cookie) se protege con SameSite=Strict y la comprobación de Origin (§8.5)
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .headers(cabeceras -> cabeceras
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(UN_ANIO_EN_SEGUNDOS)
                                .includeSubDomains(true))
                        .frameOptions(marcos -> marcos.deny())
                        .contentTypeOptions(tipos -> { })
                        .referrerPolicy(referer -> referer.policy(ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(politicaContenido)));
    }

    /** Solo el frontend Angular, con credenciales para la cookie del refresh token. */
    private CorsConfigurationSource origenesPermitidos() {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(List.of(urlFrontend));
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        // Cabeceras que el JavaScript del frontend necesita leer (las demás las oculta el navegador en CORS)
        configuracion.setExposedHeaders(List.of(HttpHeaders.LOCATION, HttpHeaders.RETRY_AFTER,
                ProductoServicio.CABECERA_NO_DISPONIBLE));
        configuracion.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource origen = new UrlBasedCorsConfigurationSource();
        origen.registerCorsConfiguration("/**", configuracion);
        return origen;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(COSTE_BCRYPT);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuracion) throws Exception {
        return configuracion.getAuthenticationManager();
    }
}
