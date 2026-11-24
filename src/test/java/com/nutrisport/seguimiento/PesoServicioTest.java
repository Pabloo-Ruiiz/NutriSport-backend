package com.nutrisport.seguimiento;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutrisport.IntegracionBase;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.seguridad.UsuarioAutenticado;
import com.nutrisport.perfil.entidad.NivelActividad;
import com.nutrisport.perfil.entidad.ObjetivoDeportivo;
import com.nutrisport.perfil.entidad.PerfilDeportivo;
import com.nutrisport.perfil.entidad.Sexo;
import com.nutrisport.perfil.repositorio.PerfilDeportivoRepositorio;
import com.nutrisport.seguimiento.dto.PesoRequest;
import com.nutrisport.seguimiento.dto.SeriePesoResponse;
import com.nutrisport.seguimiento.dto.SeriePesoResponse.PuntoPeso;
import com.nutrisport.seguimiento.servicio.PesoServicio;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Pesajes: media móvil de 7 días y sincronización del peso actual del perfil con el más reciente. */
class PesoServicioTest extends IntegracionBase {

    private final PesoServicio pesoServicio;
    private final PerfilDeportivoRepositorio perfilRepositorio;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    private Usuario usuario;
    private LocalDate hoy;

    @Autowired
    PesoServicioTest(PesoServicio pesoServicio, PerfilDeportivoRepositorio perfilRepositorio, JdbcTemplate jdbc,
            Clock reloj) {
        this.pesoServicio = pesoServicio;
        this.perfilRepositorio = perfilRepositorio;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE usuario CASCADE");
        usuario = crearUsuarioYToken().usuario();
        hoy = LocalDate.now(reloj);
        guardarPerfil(usuario);
        autenticarComo(usuario);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        jdbc.execute("TRUNCATE usuario CASCADE");
    }

    @Test
    void laMediaMovilEsLaDeLosPesajesDeLosSieteDiasQueTerminanEnCadaFecha() {
        registrar(hoy.minusDays(8), "70.00");
        registrar(hoy.minusDays(4), "71.00");
        registrar(hoy, "72.50");

        SeriePesoResponse serie = pesoServicio.serie(hoy.minusDays(8), hoy);

        assertThat(serie.puntos()).extracting(PuntoPeso::fecha)
                .containsExactly(hoy.minusDays(8), hoy.minusDays(4), hoy);
        // d−8: solo él → 70,00 · d−4: d−10..d−4 → (70 + 71) / 2 · hoy: d−6..hoy → (71 + 72,5) / 2
        assertThat(serie.puntos()).extracting(PuntoPeso::mediaMovil7)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("70.00"), new BigDecimal("70.50"), new BigDecimal("71.75"));
        assertThat(serie.variacion()).isEqualByComparingTo("2.50");
    }

    @Test
    void elPesajeMasRecienteActualizaElPerfilYRecalculaSusObjetivos() {
        BigDecimal kcalAntes = perfil().getObjetivoKcal();

        registrar(hoy, "80.00");

        PerfilDeportivo perfil = perfil();
        assertThat(perfil.getPesoActualKg()).isEqualByComparingTo("80");
        assertThat(perfil.getObjetivoKcal()).isNotEqualByComparingTo(kcalAntes);
    }

    @Test
    void unPesajeAntiguoNoCambiaElPerfil() {
        registrar(hoy, "72.50");
        BigDecimal kcalConElUltimo = perfil().getObjetivoKcal();

        registrar(hoy.minusDays(4), "65.00");

        PerfilDeportivo perfil = perfil();
        assertThat(perfil.getPesoActualKg()).isEqualByComparingTo("72.50");
        assertThat(perfil.getObjetivoKcal()).isEqualByComparingTo(kcalConElUltimo);
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    private void registrar(LocalDate fecha, String pesoKg) {
        pesoServicio.registrar(new PesoRequest(fecha, new BigDecimal(pesoKg)));
    }

    private PerfilDeportivo perfil() {
        return perfilRepositorio.findByUsuarioId(usuario.getId()).orElseThrow();
    }

    /** PesoServicio toma el usuario del contexto de seguridad, como en una petición real. */
    private static void autenticarComo(Usuario usuario) {
        UsuarioAutenticado autenticado = UsuarioAutenticado.de(usuario);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(autenticado, null, autenticado.getAuthorities()));
    }

    /** Perfil de 70 kg con objetivos calculados (no manuales). */
    private void guardarPerfil(Usuario propietario) {
        PerfilDeportivo perfil = new PerfilDeportivo();
        perfil.setUsuario(propietario);
        perfil.setFechaNacimiento(LocalDate.of(1996, 6, 1));
        perfil.setSexo(Sexo.HOMBRE);
        perfil.setAlturaCm(new BigDecimal("175"));
        perfil.setPesoActualKg(new BigDecimal("70"));
        perfil.setObjetivo(ObjetivoDeportivo.MANTENER);
        perfil.setDeportePrincipal("CARRERA_9_7KMH");
        perfil.setNivelActividad(NivelActividad.MODERADO);
        perfil.setDiasEntrenamientoSemana((short) 4);
        perfil.setDuracionMediaMin((short) 60);
        perfil.setComidasPorDia((short) 4);
        perfil.setObjetivoKcal(new BigDecimal("2308"));
        perfil.setObjetivoProteinaG(new BigDecimal("98"));
        perfil.setObjetivoCarbohidratosG(new BigDecimal("335"));
        perfil.setObjetivoGrasaG(new BigDecimal("64"));
        perfilRepositorio.save(perfil);
    }
}
