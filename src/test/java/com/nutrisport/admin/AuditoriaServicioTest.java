package com.nutrisport.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.IntegracionBase;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.seguridad.UsuarioAutenticado;
import com.nutrisport.admin.servicio.AuditoriaServicio;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuditoriaServicioTest extends IntegracionBase {

    private static final String IP = "203.0.113.42";

    private final AuditoriaServicio auditoriaServicio;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager gestorTransacciones;

    private Usuario administrador;

    @Autowired
    AuditoriaServicioTest(AuditoriaServicio auditoriaServicio, JdbcTemplate jdbc,
            PlatformTransactionManager gestorTransacciones) {
        this.auditoriaServicio = auditoriaServicio;
        this.jdbc = jdbc;
        this.gestorTransacciones = gestorTransacciones;
    }

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE usuario CASCADE");
        administrador = crearUsuarioYToken().usuario();
        administrador.anadirRol(Rol.ADMIN);
        UsuarioAutenticado principal = UsuarioAutenticado.de(administrador);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.setRemoteAddr(IP);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void registraAdministradorIpDetalleYComparteLaTransaccionDeLaAccion() {
        Map<String, Object> detalle = Map.of("campo", "estado", "valor", "BLOQUEADO");
        TransactionTemplate transaccion = new TransactionTemplate(gestorTransacciones);

        assertThatThrownBy(() -> transaccion.executeWithoutResult(estado -> {
            auditoriaServicio.registrar("BLOQUEAR", "usuario", administrador.getId().toString(), detalle);
            throw new IllegalStateException("Forzar rollback de la acción");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria", Integer.class)).isZero();

        auditoriaServicio.registrar("BLOQUEAR", "usuario", administrador.getId().toString(), detalle);

        assertThat(jdbc.queryForObject("SELECT admin_id FROM auditoria", java.util.UUID.class))
                .isEqualTo(administrador.getId());
        assertThat(jdbc.queryForObject("SELECT ip FROM auditoria", String.class)).isEqualTo(IP);
        assertThat(jdbc.queryForObject("SELECT detalle->>'campo' FROM auditoria", String.class))
                .isEqualTo("estado");
        assertThat(jdbc.queryForObject("SELECT detalle->>'valor' FROM auditoria", String.class))
                .isEqualTo("BLOQUEADO");
        assertThat(jdbc.queryForObject("SELECT creado_en IS NOT NULL AND actualizado_en IS NOT NULL FROM auditoria",
                Boolean.class)).isTrue();
    }
}
