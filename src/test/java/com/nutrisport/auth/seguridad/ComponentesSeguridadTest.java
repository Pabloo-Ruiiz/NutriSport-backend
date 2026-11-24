package com.nutrisport.auth.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.comun.config.JwtProperties;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import tools.jackson.databind.json.JsonMapper;

/** Pruebas unitarias del filtro JWT, las respuestas 401/403 y {@link SeguridadUtils}. */
class ComponentesSeguridadTest {

    private final JwtServicio jwtServicio = new JwtServicio(
            new JwtProperties("secreto-de-tests-nutrisport-no-usar-en-produccion-0123456789", 15, 7));
    private final UsuarioDetailsServiceImpl detailsService = mock(UsuarioDetailsServiceImpl.class);
    private final FiltroJwt filtro = new FiltroJwt(jwtServicio, detailsService);
    private final EscritorProblemas escritor = new EscritorProblemas(JsonMapper.builder().build());

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void filtroAutenticaConUnTokenValidoYCargaElUsuarioPorId() throws Exception {
        UsuarioAutenticado usuario = usuario(EstadoUsuario.ACTIVO);
        when(detailsService.cargarPorId(usuario.getId())).thenReturn(usuario);

        filtro.doFilter(peticionConToken(jwtServicio.generarAccessToken(usuario)), new MockHttpServletResponse(),
                new MockFilterChain());

        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        assertThat(autenticacion).isNotNull();
        assertThat(autenticacion.getPrincipal()).isEqualTo(usuario);
        assertThat(autenticacion.getCredentials()).isNull();
        assertThat(autenticacion.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_DEPORTISTA");
        assertThat(new SeguridadUtils().usuarioActualId()).isEqualTo(usuario.getId());
    }

    @Test
    void filtroNoAutenticaAUnUsuarioBloqueadoNiAUnoQueYaNoExiste() throws Exception {
        UsuarioAutenticado bloqueado = usuario(EstadoUsuario.BLOQUEADO);
        when(detailsService.cargarPorId(bloqueado.getId())).thenReturn(bloqueado);
        filtro.doFilter(peticionConToken(jwtServicio.generarAccessToken(bloqueado)), new MockHttpServletResponse(),
                new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        UsuarioAutenticado borrado = usuario(EstadoUsuario.ACTIVO);
        when(detailsService.cargarPorId(borrado.getId())).thenThrow(new UsernameNotFoundException("x"));
        filtro.doFilter(peticionConToken(jwtServicio.generarAccessToken(borrado)), new MockHttpServletResponse(),
                new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void filtroIgnoraTokensNoValidosYSigueLaCadena() throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        filtro.doFilter(peticionConToken("no-es-un-jwt"), new MockHttpServletResponse(), cadena);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(cadena.getRequest()).isNotNull();
        verifyNoInteractions(detailsService);
    }

    @Test
    void puntoDeEntradaEscribe401ConFormatoProblemDetails() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/v1/perfil");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        new PuntoEntradaNoAutenticado(escritor).commence(peticion, respuesta, new BadCredentialsException("x"));

        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(respuesta.getContentType()).startsWith("application/problem+json");
        assertThat(respuesta.getContentAsString())
                .contains("\"type\":\"urn:nutrisport:error:no_autenticado\"")
                .contains("\"title\":\"No autenticado\"")
                .contains("\"status\":401")
                .contains("\"instance\":\"/api/v1/perfil\"")
                .contains("\"codigo\":\"NO_AUTENTICADO\"")
                .contains("\"errors\":[]")
                .doesNotContain("\"properties\"");
    }

    @Test
    void manejadorDeAccesoDenegadoEscribe403() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/v1/admin/usuarios");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        new ManejadorAccesoDenegado(escritor).handle(peticion, respuesta, new AccessDeniedException("x"));

        assertThat(respuesta.getStatus()).isEqualTo(403);
        assertThat(respuesta.getContentType()).startsWith("application/problem+json");
        assertThat(respuesta.getContentAsString())
                .contains("\"codigo\":\"SIN_PERMISO\"")
                .contains("\"type\":\"urn:nutrisport:error:sin_permiso\"");
    }

    @Test
    void usuarioAutenticadoRespetaElEstadoYBorraLasCredenciales() {
        assertThat(usuario(EstadoUsuario.ACTIVO).isEnabled()).isTrue();
        assertThat(usuario(EstadoUsuario.BLOQUEADO).isEnabled()).isFalse();
        assertThat(usuario(EstadoUsuario.BLOQUEADO).isAccountNonLocked()).isFalse();
        assertThat(usuario(EstadoUsuario.ELIMINADO).isEnabled()).isFalse();
        assertThat(usuario(EstadoUsuario.ELIMINADO).isAccountNonLocked()).isTrue();

        UsuarioAutenticado usuario = usuario(EstadoUsuario.ACTIVO);
        usuario.eraseCredentials();
        assertThat(usuario.getPassword()).isNull();
    }

    @Test
    void seguridadUtilsFallaSinUsuario() {
        assertThatThrownBy(() -> new SeguridadUtils().usuarioActual())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private static MockHttpServletRequest peticionConToken(String token) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/api/v1/perfil");
        peticion.addHeader("Authorization", "Bearer " + token);
        return peticion;
    }

    private static UsuarioAutenticado usuario(EstadoUsuario estado) {
        return new UsuarioAutenticado(UUID.randomUUID(), "ana@ejemplo.com", "hash", Set.of(Rol.DEPORTISTA), estado);
    }
}
