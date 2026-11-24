package com.nutrisport.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.seguridad.JwtServicio;
import com.nutrisport.auth.seguridad.UsuarioAutenticado;
import com.nutrisport.comun.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pruebas unitarias de {@link JwtServicio}, sin contexto de Spring. */
class JwtServicioTest {

    private static final String SECRETO = "secreto-de-tests-nutrisport-no-usar-en-produccion-0123456789";

    private final JwtServicio servicio = new JwtServicio(new JwtProperties(SECRETO, 15, 7));

    @Test
    void unTokenGeneradoEsValidoYSuSubEsElId() {
        UsuarioAutenticado usuario = usuario();

        Claims claims = servicio.validar(servicio.generarAccessToken(usuario)).orElseThrow();

        assertThat(claims.getSubject()).isEqualTo(usuario.getId().toString());
        assertThat(claims.get("roles", List.class)).containsExactly("ADMIN", "DEPORTISTA");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(15 * 60 * 1000L);
        assertThat(servicio.segundosValidez()).isEqualTo(900);
    }

    @Test
    void cadaTokenTieneUnJtiDistinto() {
        UsuarioAutenticado usuario = usuario();
        String jti1 = servicio.validar(servicio.generarAccessToken(usuario)).orElseThrow().getId();
        String jti2 = servicio.validar(servicio.generarAccessToken(usuario)).orElseThrow().getId();
        assertThat(jti1).isNotEqualTo(jti2);
    }

    @Test
    void unTokenConLaFirmaAlteradaNoEsValido() {
        String token = servicio.generarAccessToken(usuario());
        // Se cambia un carácter del centro de la firma (el último puede no alterar los bytes decodificados)
        int posicion = token.lastIndexOf('.') + 10;
        char original = token.charAt(posicion);
        String alterado = token.substring(0, posicion) + (original == 'A' ? 'B' : 'A') + token.substring(posicion + 1);

        assertThat(servicio.validar(alterado)).isEmpty();
    }

    @Test
    void unTokenCaducadoNoEsValido() {
        JwtServicio servicioCaducado = new JwtServicio(new JwtProperties(SECRETO, -1, 7));

        String caducado = servicioCaducado.generarAccessToken(usuario());

        assertThat(servicioCaducado.validar(caducado)).isEmpty();
        assertThat(servicio.validar(caducado)).isEmpty();
    }

    @Test
    void rechazaTokensDeOtraClaveSinFirmaOMalFormadosSinLanzarExcepciones() {
        String deOtraClave = new JwtServicio(new JwtProperties("otro-secreto-distinto-de-al-menos-32-bytes!!", 15, 7))
                .generarAccessToken(usuario());
        String sinFirma = Jwts.builder().subject(UUID.randomUUID().toString()).compact();

        assertThat(servicio.validar(deOtraClave)).isEmpty();
        assertThat(servicio.validar(sinFirma)).isEmpty();
        assertThat(servicio.validar("esto.no.es-un-jwt")).isEmpty();
        assertThat(servicio.validar("")).isEmpty();
        assertThat(servicio.validar(null)).isEmpty();
    }

    @Test
    void unSecretoDeMenosDe32CaracteresLanzaIllegalStateException() {
        String secretoCorto = "a".repeat(31);

        assertThatThrownBy(() -> new JwtServicio(new JwtProperties(secretoCorto, 15, 7)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(secretoCorto);
        assertThatThrownBy(() -> new JwtServicio(new JwtProperties(null, 15, 7)))
                .isInstanceOf(IllegalStateException.class);
        // 32 caracteres sí es suficiente
        assertThat(new JwtServicio(new JwtProperties("a".repeat(32), 15, 7))).isNotNull();
    }

    private static UsuarioAutenticado usuario() {
        return new UsuarioAutenticado(UUID.randomUUID(), "ana@ejemplo.com", "hash",
                Set.of(Rol.DEPORTISTA, Rol.ADMIN), EstadoUsuario.ACTIVO);
    }
}
