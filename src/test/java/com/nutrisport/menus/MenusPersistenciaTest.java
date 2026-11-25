package com.nutrisport.menus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.IntegracionBase;
import com.nutrisport.admin.servicio.AdminRecetaServicio;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comun.excepcion.ConflictoException;
import com.nutrisport.menus.entidad.ComidaPlanificada;
import com.nutrisport.menus.entidad.EstadoMenu;
import com.nutrisport.menus.entidad.MenuSemanal;
import com.nutrisport.menus.entidad.TipoMenu;
import com.nutrisport.menus.repositorio.ComidaPlanificadaRepositorio;
import com.nutrisport.menus.repositorio.MenuSemanalRepositorio;
import com.nutrisport.recetas.entidad.OrigenReceta;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Migración V14 (menús y comidas planificadas), entidades y repositorios, contra PostgreSQL real. */
class MenusPersistenciaTest extends IntegracionBase {

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 30);

    @Autowired
    private MenuSemanalRepositorio menuRepositorio;
    @Autowired
    private ComidaPlanificadaRepositorio comidaRepositorio;
    @Autowired
    private RecetaRepositorio recetaRepositorio;
    @Autowired
    private AdminRecetaServicio adminRecetaServicio;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transacciones;

    private Usuario ana;
    private Receta avena;
    private Receta pollo;

    @BeforeEach
    void preparar() {
        limpiar();
        ana = crearUsuarioYToken().usuario();
        avena = receta("Gachas de avena", Visibilidad.PUBLICO, null);
        pollo = receta("Pollo con arroz", Visibilidad.PUBLICO, null);
    }

    @AfterEach
    void limpiar() {
        jdbc.execute("TRUNCATE menu_semanal, receta, registro_comida, usuario CASCADE");
    }

    @Test
    void guardaUnMenuConSusComidasOrdenadasPorFechaYOrden() {
        UUID id = menu("Semana carga 10K", LUNES, LUNES.plusDays(6), false, menu -> {
            menu.anadirComida(new ComidaPlanificada(LUNES.plusDays(1), TipoComida.COMIDA, pollo, new BigDecimal("1.5")));
            menu.anadirComida(new ComidaPlanificada(LUNES, TipoComida.DESAYUNO, avena, BigDecimal.ONE));
            menu.anadirComida(new ComidaPlanificada(LUNES, TipoComida.CENA, pollo, new BigDecimal("2")));
        }).getId();

        transacciones.executeWithoutResult(tx -> {
            MenuSemanal menu = menuRepositorio.findByIdAndUsuarioId(id, ana.getId()).orElseThrow();
            assertThat(menu.getEstado()).isEqualTo(EstadoMenu.BORRADOR);
            assertThat(menu.isGeneradoAutomaticamente()).isFalse();
            assertThat(menu.contiene(LUNES.plusDays(6))).isTrue();
            assertThat(menu.contiene(LUNES.plusDays(7))).isFalse();
            // Por fecha y, dentro de cada fecha, por orden de alta
            assertThat(menu.getComidas()).extracting(ComidaPlanificada::getFecha)
                    .containsExactly(LUNES, LUNES, LUNES.plusDays(1));
            assertThat(menu.getComidas()).extracting(ComidaPlanificada::getTipoComida)
                    .containsExactly(TipoComida.DESAYUNO, TipoComida.CENA, TipoComida.COMIDA);
            assertThat(menu.getComidas().get(2).getReceta().getNombre()).isEqualTo("Pollo con arroz");
            assertThat(menu.getComidas().get(2).getRaciones()).isEqualByComparingTo("1.5");
        });
        // De otro usuario no existe
        assertThat(menuRepositorio.findByIdAndUsuarioId(id, crearUsuarioYToken().usuario().getId())).isEmpty();
    }

    @Test
    void quitarUnaComidaLaBorra() {
        UUID id = menu("Lunes", LUNES, LUNES, false, menu -> {
            menu.anadirComida(new ComidaPlanificada(LUNES, TipoComida.DESAYUNO, avena, BigDecimal.ONE));
            menu.anadirComida(new ComidaPlanificada(LUNES, TipoComida.COMIDA, pollo, BigDecimal.ONE));
        }).getId();

        transacciones.executeWithoutResult(tx -> {
            MenuSemanal menu = menuRepositorio.findByIdAndUsuarioId(id, ana.getId()).orElseThrow();
            menu.quitarComida(menu.getComidas().get(0));
        });

        assertThat(comidaRepositorio.count()).isEqualTo(1);
    }

    @Test
    void elListadoFiltraPorFavoritoYPorFecha() {
        menu("Pasado favorito", LUNES.minusDays(14), LUNES.minusDays(8), true, m -> { });
        menu("Esta semana", LUNES, LUNES.plusDays(6), false, m -> { });
        menu("Próxima favorito", LUNES.plusDays(7), LUNES.plusDays(13), true, m -> { });
        menu("Ajeno", LUNES, LUNES.plusDays(6), true, m -> m.setUsuario(crearUsuarioYToken().usuario()));

        assertThat(nombres(null, null)).containsExactly("Próxima favorito", "Esta semana", "Pasado favorito");
        assertThat(nombres(true, null)).containsExactly("Próxima favorito", "Pasado favorito");
        assertThat(nombres(null, LUNES.plusDays(3))).containsExactly("Próxima favorito", "Esta semana");
        assertThat(nombres(false, LUNES)).containsExactly("Esta semana");
    }

    @Test
    void lasRestriccionesImpidenMenusYComidasIncoherentes() {
        assertThatThrownBy(() -> menu("Al revés", LUNES, LUNES.minusDays(1), false, m -> { }))
                .isInstanceOf(DataIntegrityViolationException.class);
        for (String raciones : List.of("0", "10.5")) {
            assertThatThrownBy(() -> menu("Raciones " + raciones, LUNES, LUNES, false, m -> m.anadirComida(
                    new ComidaPlanificada(LUNES, TipoComida.COMIDA, pollo, new BigDecimal(raciones)))))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        UUID menuId = menu("Válido", LUNES, LUNES, false, m -> m.anadirComida(
                new ComidaPlanificada(LUNES, TipoComida.COMIDA, pollo, BigDecimal.ONE))).getId();
        UUID comidaId = comidaRepositorio.findAll().get(0).getId();
        // Un registro enlazado exige consumida = true
        UUID registro = registroComida(ana);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE comida_planificada SET registro_comida_id = ?, consumida = false WHERE id = ?", registro, comidaId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE menu_semanal SET estado = 'PAUSADO' WHERE id = ?", menuId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void alBorrarLaComidaRegistradaElEnlaceQuedaNuloYSigueConsumida() {
        UUID registro = registroComida(ana);
        menu("Lunes", LUNES, LUNES, false, m -> m.anadirComida(
                new ComidaPlanificada(LUNES, TipoComida.COMIDA, pollo, BigDecimal.ONE)));
        UUID comidaId = comidaRepositorio.findAll().get(0).getId();
        jdbc.update("UPDATE comida_planificada SET registro_comida_id = ?, consumida = true WHERE id = ?", registro, comidaId);

        jdbc.update("DELETE FROM registro_comida WHERE id = ?", registro);

        assertThat(jdbc.queryForObject("SELECT registro_comida_id FROM comida_planificada WHERE id = ?", UUID.class,
                comidaId)).isNull();
        assertThat(jdbc.queryForObject("SELECT consumida FROM comida_planificada WHERE id = ?", Boolean.class,
                comidaId)).isTrue();
    }

    @Test
    void unaRecetaPlanificadaNoSePuedeBorrar() {
        Receta propia = receta("Receta del administrador", Visibilidad.PUBLICO, null);
        menu("Lunes", LUNES, LUNES, false, m -> m.anadirComida(
                new ComidaPlanificada(LUNES, TipoComida.COMIDA, propia, BigDecimal.ONE)));

        assertThat(comidaRepositorio.recetaPlanificada(propia.getId())).isTrue();
        // La FK aplazada lo impide al confirmar...
        assertThatThrownBy(() -> jdbc.update("DELETE FROM receta WHERE id = ?", propia.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        // ...y la administración lo comprueba antes con un 409
        assertThatThrownBy(() -> adminRecetaServicio.eliminar(propia.getId()))
                .isInstanceOfSatisfying(ConflictoException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("RECETA_PLANIFICADA"));
    }

    @Test
    void borrarUnaCuentaBorraSusMenusAunqueUsenSusRecetasPrivadas() {
        Receta privada = receta("Tostadas de Ana", Visibilidad.PRIVADO, ana);
        menu("Semana de Ana", LUNES, LUNES.plusDays(6), false, m -> {
            m.anadirComida(new ComidaPlanificada(LUNES, TipoComida.DESAYUNO, privada, BigDecimal.ONE));
            m.anadirComida(new ComidaPlanificada(LUNES, TipoComida.COMIDA, pollo, BigDecimal.ONE));
        });

        jdbc.update("DELETE FROM usuario WHERE id = ?", ana.getId());

        assertThat(menuRepositorio.count()).isZero();
        assertThat(comidaRepositorio.count()).isZero();
        assertThat(recetaRepositorio.findAll()).extracting(Receta::getNombre)
                .containsExactlyInAnyOrder("Gachas de avena", "Pollo con arroz");
    }

    // ---------------------------------------------------------------------------------------------

    private MenuSemanal menu(String nombre, LocalDate inicio, LocalDate fin, boolean favorito,
            java.util.function.Consumer<MenuSemanal> configurar) {
        MenuSemanal menu = new MenuSemanal();
        menu.setUsuario(ana);
        menu.setNombre(nombre);
        menu.setTipo(inicio.equals(fin) ? TipoMenu.DIARIO : TipoMenu.SEMANAL);
        menu.setFechaInicio(inicio);
        menu.setFechaFin(fin);
        menu.setFavorito(favorito);
        configurar.accept(menu);
        return menuRepositorio.saveAndFlush(menu);
    }

    private List<String> nombres(Boolean favorito, LocalDate desde) {
        return menuRepositorio.buscar(ana.getId(), favorito, desde, PageRequest.of(0, 20))
                .map(MenuSemanal::getNombre).getContent();
    }

    private Receta receta(String nombre, Visibilidad visibilidad, Usuario propietario) {
        Receta receta = new Receta();
        receta.setOrigen(OrigenReceta.PROPIA);
        receta.setNombre(nombre);
        receta.setVisibilidad(visibilidad);
        receta.setPropietario(propietario);
        receta.setPublicada(true);
        return recetaRepositorio.saveAndFlush(receta);
    }

    private UUID registroComida(Usuario usuario) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO registro_comida (id, usuario_id, fecha, tipo_comida, origen, total_kcal, total_proteina,
                  total_carbohidratos, total_grasa, creado_en, actualizado_en)
                VALUES (?, ?, ?, 'COMIDA', 'PLAN', 0, 0, 0, 0, now(), now())""", id, usuario.getId(), LUNES);
        return id;
    }
}
