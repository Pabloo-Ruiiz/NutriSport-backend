package com.nutrisport.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nutrisport.IntegracionBase;
import com.nutrisport.actividad.entidad.Entrenamiento;
import com.nutrisport.actividad.entidad.EstadoEntrenamiento;
import com.nutrisport.actividad.entidad.Intensidad;
import com.nutrisport.actividad.repositorio.EntrenamientoRepositorio;
import com.nutrisport.actividad.repositorio.TipoActividadRepositorio;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.perfil.dto.ObjetivoDiarioResponse;
import com.nutrisport.perfil.entidad.NivelActividad;
import com.nutrisport.perfil.entidad.ObjetivoDeportivo;
import com.nutrisport.perfil.entidad.PerfilDeportivo;
import com.nutrisport.perfil.entidad.Sexo;
import com.nutrisport.perfil.repositorio.PerfilDeportivoRepositorio;
import com.nutrisport.perfil.servicio.ObjetivoDiarioServicio;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Objetivo de cada día: base del perfil + gasto neto de los entrenamientos que cuentan, con el extra en
 * carbohidratos (§3.B, §3.G y §6.1 ObjetivoDiario).
 */
@AutoConfigureMockMvc
class ObjetivoDiarioServicioTest extends IntegracionBase {

    private final ObjetivoDiarioServicio objetivoDiarioServicio;
    private final PerfilDeportivoRepositorio perfilRepositorio;
    private final EntrenamientoRepositorio entrenamientoRepositorio;
    private final TipoActividadRepositorio tipoActividadRepositorio;
    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    private UsuarioConToken deportista;
    private UUID usuarioId;
    private LocalDate hoy;

    @Autowired
    ObjetivoDiarioServicioTest(ObjetivoDiarioServicio objetivoDiarioServicio,
            PerfilDeportivoRepositorio perfilRepositorio, EntrenamientoRepositorio entrenamientoRepositorio,
            TipoActividadRepositorio tipoActividadRepositorio, MockMvc mockMvc, JdbcTemplate jdbc, Clock reloj) {
        this.objetivoDiarioServicio = objetivoDiarioServicio;
        this.perfilRepositorio = perfilRepositorio;
        this.entrenamientoRepositorio = entrenamientoRepositorio;
        this.tipoActividadRepositorio = tipoActividadRepositorio;
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @BeforeEach
    void preparar() {
        vaciarTablas();
        deportista = crearUsuarioYToken();
        usuarioId = deportista.usuario().getId();
        hoy = LocalDate.now(reloj);
        guardarPerfilConObjetivos(deportista.usuario());
    }

    @AfterEach
    void limpiar() {
        vaciarTablas();
    }

    @Test
    void sinEntrenamientosElTotalEsLaBase() {
        ObjetivoDiarioResponse objetivo = objetivoDiarioServicio.obtener(usuarioId, hoy);

        assertThat(objetivo.kcalEntreno()).isEqualByComparingTo("0");
        assertThat(objetivo.base().kcal()).isEqualByComparingTo("2308");
        assertThat(objetivo.total()).isEqualTo(objetivo.base());
    }

    @Test
    void unEntrenamientoDe616KcalSeSumaALaEnergiaYALosCarbohidratos() {
        guardarEntrenamiento(hoy, EstadoEntrenamiento.PLANIFICADO, "616");

        ObjetivoDiarioResponse objetivo = objetivoDiarioServicio.recalcular(usuarioId, hoy);

        assertThat(objetivo.kcalEntreno()).isEqualByComparingTo("616");
        assertThat(objetivo.total().kcal()).isEqualByComparingTo("2924");
        // 335 + 616 / 4
        assertThat(objetivo.total().carbohidratos()).isEqualByComparingTo("489");
        // La proteína y la grasa no cambian
        assertThat(objetivo.total().proteina()).isEqualByComparingTo("98");
        assertThat(objetivo.total().grasa()).isEqualByComparingTo("64");
    }

    @Test
    void marcarElEntrenamientoComoOmitidoDevuelveElObjetivoALaBase() {
        Entrenamiento entrenamiento = guardarEntrenamiento(hoy, EstadoEntrenamiento.PLANIFICADO, "616");
        objetivoDiarioServicio.recalcular(usuarioId, hoy);

        entrenamiento.setEstado(EstadoEntrenamiento.OMITIDO);
        entrenamientoRepositorio.save(entrenamiento);
        ObjetivoDiarioResponse objetivo = objetivoDiarioServicio.recalcular(usuarioId, hoy);

        assertThat(objetivo.kcalEntreno()).isEqualByComparingTo("0");
        assertThat(objetivo.total()).isEqualTo(objetivo.base());
    }

    @Test
    void unEntrenamientoDelDiaSiguienteNoCuenta() {
        guardarEntrenamiento(hoy.plusDays(1), EstadoEntrenamiento.PLANIFICADO, "616");

        ObjetivoDiarioResponse objetivo = objetivoDiarioServicio.recalcular(usuarioId, hoy);

        assertThat(objetivo.kcalEntreno()).isEqualByComparingTo("0");
        assertThat(objetivo.total().kcal()).isEqualByComparingTo("2308");
    }

    @Test
    void cambiarElPesoDelPerfilNoModificaElObjetivoDeAyer() throws Exception {
        LocalDate ayer = hoy.minusDays(1);
        ObjetivoDiarioResponse objetivoDeAyer = objetivoDiarioServicio.obtener(usuarioId, ayer);
        ObjetivoDiarioResponse objetivoDeHoy = objetivoDiarioServicio.obtener(usuarioId, hoy);

        cambiarPeso("85");

        assertThat(objetivoDiarioServicio.obtener(usuarioId, ayer)).isEqualTo(objetivoDeAyer);
        // El de hoy sí se recalcula con el perfil nuevo
        assertThat(objetivoDiarioServicio.obtener(usuarioId, hoy).base().kcal())
                .isNotEqualByComparingTo(objetivoDeHoy.base().kcal());
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    /** Perfil con objetivos base fijos (2308 kcal · 98 P · 335 HC · 64 G) para no depender de la edad. */
    private void guardarPerfilConObjetivos(Usuario usuario) {
        PerfilDeportivo perfil = new PerfilDeportivo();
        perfil.setUsuario(usuario);
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

    /** Entrenamiento a las 10:00 (hora de la aplicación) con un gasto neto dado. */
    private Entrenamiento guardarEntrenamiento(LocalDate fecha, EstadoEntrenamiento estado, String kcal) {
        Entrenamiento entrenamiento = new Entrenamiento();
        entrenamiento.setUsuario(deportista.usuario());
        entrenamiento.setTipoActividad(tipoActividadRepositorio.findById("CARRERA_9_7KMH").orElseThrow());
        entrenamiento.setFechaHora(fecha.atTime(10, 0).atZone(reloj.getZone()).toOffsetDateTime());
        entrenamiento.setDuracionMin((short) 60);
        entrenamiento.setIntensidad(Intensidad.MEDIA);
        entrenamiento.setEstado(estado);
        entrenamiento.setKcalEstimadas(new BigDecimal(kcal));
        return entrenamientoRepositorio.save(entrenamiento);
    }

    /** Cambia el peso por la API, como lo haría el usuario (PerfilServicio recalcula hoy y los días futuros). */
    private void cambiarPeso(String pesoKg) throws Exception {
        mockMvc.perform(put("/api/v1/perfil")
                        .header("Authorization", deportista.cabeceraAutorizacion())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaNacimiento": "1996-06-01", "sexo": "HOMBRE", "alturaCm": 175, "pesoKg": %s,
                                 "objetivo": "MANTENER", "deportePrincipal": "CARRERA_9_7KMH", "nivelActividad": "MODERADO",
                                 "diasEntrenamientoSemana": 4, "duracionMediaMin": 60, "comidasPorDia": 4}
                                """.formatted(pesoKg)))
                .andExpect(status().isOk());
    }

    private void vaciarTablas() {
        jdbc.execute("TRUNCATE usuario CASCADE");
    }
}
