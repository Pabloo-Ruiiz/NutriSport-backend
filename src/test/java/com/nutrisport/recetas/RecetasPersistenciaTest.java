package com.nutrisport.recetas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nutrisport.IntegracionBase;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.UnidadBase;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.catalogo.servicio.UsosAlimento;
import com.nutrisport.recetas.entidad.EstadoConversion;
import com.nutrisport.recetas.entidad.EstadoNutricional;
import com.nutrisport.recetas.entidad.OrigenReceta;
import com.nutrisport.recetas.entidad.PasoReceta;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.entidad.RecetaFavorita;
import com.nutrisport.recetas.entidad.RecetaIngrediente;
import com.nutrisport.recetas.repositorio.RecetaFavoritaRepositorio;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Migración V12 (recetas, ingredientes, favoritas y categorías equivalentes de TheMealDB), entidades y
 * repositorios, contra PostgreSQL real.
 */
class RecetasPersistenciaTest extends IntegracionBase {

    private static final UUID CATEGORIA_POLLO = UUID.fromString("c0000000-0000-0000-0001-000000000007");
    private static final UUID CATEGORIA_POSTRES = UUID.fromString("c0000000-0000-0000-0001-000000000015");
    private static final UUID CATEGORIA_CARNES = UUID.fromString("c0000000-0000-0000-0000-000000000003");

    private final RecetaRepositorio recetaRepositorio;
    private final RecetaFavoritaRepositorio favoritaRepositorio;
    private final AlimentoRepositorio alimentoRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final UsosAlimento usosAlimento;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacciones;

    private Usuario ana;
    private Usuario bruno;

    @Autowired
    RecetasPersistenciaTest(RecetaRepositorio recetaRepositorio, RecetaFavoritaRepositorio favoritaRepositorio,
            AlimentoRepositorio alimentoRepositorio, CategoriaRepositorio categoriaRepositorio,
            UsosAlimento usosAlimento, JdbcTemplate jdbc, TransactionTemplate transacciones) {
        this.recetaRepositorio = recetaRepositorio;
        this.favoritaRepositorio = favoritaRepositorio;
        this.alimentoRepositorio = alimentoRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
        this.usosAlimento = usosAlimento;
        this.jdbc = jdbc;
        this.transacciones = transacciones;
    }

    @BeforeEach
    void preparar() {
        limpiar();
        ana = crearUsuarioYToken().usuario();
        bruno = crearUsuarioYToken().usuario();
    }

    @AfterEach
    void limpiar() {
        jdbc.execute("TRUNCATE receta, alimento, usuario CASCADE");
    }

    // ---------------------------------------------------------------------------------------------
    // Categorías equivalentes de TheMealDB
    // ---------------------------------------------------------------------------------------------

    @Test
    void lasCategoriasDeRecetaTienenSuEquivalenteDeTheMealDb() {
        Map<String, String> equivalentes = Map.ofEntries(
                Map.entry("Chicken", "Pollo"), Map.entry("Beef", "Ternera"), Map.entry("Seafood", "Pescado y marisco"),
                Map.entry("Vegetarian", "Vegetariana"), Map.entry("Vegan", "Vegana"), Map.entry("Pasta", "Pasta"),
                Map.entry("Breakfast", "Desayunos"), Map.entry("Starter", "Entrantes"),
                Map.entry("Side", "Acompañamientos"), Map.entry("Dessert", "Postres"), Map.entry("Lamb", "Cordero"),
                Map.entry("Pork", "Cerdo"), Map.entry("Goat", "Cabra"), Map.entry("Miscellaneous", "Otras"));

        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT codigo_externo, nombre, tipo FROM categoria WHERE codigo_externo IS NOT NULL");

        assertThat(filas).hasSize(14).allSatisfy(fila -> {
            assertThat(fila.get("tipo")).isEqualTo("RECETA");
            assertThat(fila.get("nombre")).isEqualTo(equivalentes.get((String) fila.get("codigo_externo")));
        });
        assertThat(categoriaRepositorio.findById(CATEGORIA_POLLO).orElseThrow().getCodigoExterno()).isEqualTo("Chicken");
    }

    @Test
    void unaCategoriaDeAlimentoNoPuedeTenerCodigoExternoNiRepetirse() {
        assertThatThrownBy(() -> jdbc.update("UPDATE categoria SET codigo_externo = 'Beef' WHERE id = ?", CATEGORIA_CARNES))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE categoria SET codigo_externo = 'Chicken' WHERE id = ?",
                CATEGORIA_POSTRES))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------------------------------------
    // Receta e ingredientes
    // ---------------------------------------------------------------------------------------------

    @Test
    void guardaUnaRecetaDeTheMealDbConSusIngredientesPasosYValoresPorDefecto() {
        Alimento pollo = alimento("Pechuga de pollo", null);
        UUID id = recetaDeTheMealDb("52850", "Chicken Couscous", receta -> {
            RecetaIngrediente conAlimento = new RecetaIngrediente("Chicken Breast", "200g");
            conAlimento.setAlimento(pollo);
            conAlimento.setCantidadGramos(new BigDecimal("200.0"));
            conAlimento.setEstadoConversion(EstadoConversion.EXACTA);
            receta.anadirIngrediente(new RecetaIngrediente("Olive Oil", "1 tbsp"));
            receta.anadirIngrediente(conAlimento);
            receta.anadirIngrediente(new RecetaIngrediente("Ginger", "pinch"));
            receta.setPasos(List.of(new PasoReceta(1, "Heat the olive oil in a large frying pan..."),
                    new PasoReceta(2, "Add the chicken and cook for 5 mins.")));
            receta.setFranjasAptas(List.of("COMIDA", "CENA"));
            receta.setEtiquetas(List.of("alta-proteina"));
        }).getId();

        transacciones.executeWithoutResult(estado -> {
            Receta receta = recetaRepositorio.findWithIngredientesById(id).orElseThrow();
            assertThat(receta.getNombreNormalizado()).isEqualTo("chicken couscous");
            assertThat(receta.getIdioma()).isEqualTo("en");
            assertThat(receta.getRaciones()).isEqualTo((short) 4);
            assertThat(receta.isRacionesSupuestas()).isTrue();
            assertThat(receta.getEstadoNutricional()).isEqualTo(EstadoNutricional.INCOMPLETO);
            assertThat(receta.getCompletitudPct()).isZero();
            assertThat(receta.isAlergenosVerificados()).isFalse();
            assertThat(receta.isPublicada()).isFalse();
            assertThat(receta.getKcalRacion()).isNull();
            assertThat(receta.getCategoria().getId()).isEqualTo(CATEGORIA_POLLO);
            assertThat(receta.getPasos()).extracting(PasoReceta::orden).containsExactly(1, 2);
            assertThat(receta.getFranjasAptas()).containsExactly("COMIDA", "CENA");
            assertThat(receta.getEtiquetas()).containsExactly("alta-proteina");
            assertThat(receta.getIngredientes()).extracting(RecetaIngrediente::getNombreOriginal)
                    .containsExactly("Olive Oil", "Chicken Breast", "Ginger");
            assertThat(receta.getIngredientes()).extracting(RecetaIngrediente::getOrden)
                    .containsExactly((short) 1, (short) 2, (short) 3);
            RecetaIngrediente polloReceta = receta.getIngredientes().get(1);
            assertThat(polloReceta.getAlimento().getNombre()).isEqualTo("Pechuga de pollo");
            assertThat(polloReceta.getCantidadGramos()).isEqualByComparingTo("200.0");
            assertThat(receta.getIngredientes().get(0).getEstadoConversion()).isEqualTo(EstadoConversion.SIN_DATOS);
        });
    }

    @Test
    void unaRecetaImportadaSeIdentificaPorSuIdMealYNoSePuedeRepetir() {
        recetaDeTheMealDb("52850", "Chicken Couscous", receta -> { });

        assertThat(recetaRepositorio.findByOrigenAndReferenciaExterna(OrigenReceta.THEMEALDB, "52850")).isPresent();
        assertThatThrownBy(() -> recetaDeTheMealDb("52850", "Otra", receta -> { }))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lasRestriccionesImpidenRecetasIncoherentes() {
        // Importada sin idMeal
        assertThatThrownBy(() -> guardar(receta(OrigenReceta.THEMEALDB, null, "Sin id", Visibilidad.PUBLICO, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Propia con referencia externa
        assertThatThrownBy(() -> guardar(receta(OrigenReceta.PROPIA, "123", "Con id", Visibilidad.PUBLICO, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Privada sin propietario
        assertThatThrownBy(() -> guardar(receta(OrigenReceta.PROPIA, null, "Huérfana", Visibilidad.PRIVADO, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Franja que no es un TipoComida
        Receta conFranjaRara = receta(OrigenReceta.PROPIA, null, "Rara", Visibilidad.PUBLICO, null);
        conFranjaRara.setFranjasAptas(List.of("APERITIVO"));
        assertThatThrownBy(() -> guardar(conFranjaRara)).isInstanceOf(DataIntegrityViolationException.class);
        // Cero raciones
        Receta sinRaciones = receta(OrigenReceta.PROPIA, null, "Vacía", Visibilidad.PUBLICO, null);
        sinRaciones.setRaciones((short) 0);
        assertThatThrownBy(() -> guardar(sinRaciones)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(recetaRepositorio.count()).isZero();
    }

    // ---------------------------------------------------------------------------------------------
    // Búsqueda
    // ---------------------------------------------------------------------------------------------

    @Test
    void laBusquedaCombinaTextoCategoriaOrigenEIngredienteYRespetaLaVisibilidad() {
        Alimento pollo = alimento("Pechuga de pollo", null);
        recetaDeTheMealDb("52850", "Chicken Couscous", receta -> {
            RecetaIngrediente ingrediente = new RecetaIngrediente("Chicken Breast", "200g");
            ingrediente.setAlimento(pollo);
            receta.anadirIngrediente(ingrediente);
        });
        recetaDeTheMealDb("52772", "Teriyaki Chicken Casserole",
                receta -> receta.anadirIngrediente(new RecetaIngrediente("chicken thighs", "2 lbs")));
        Receta tortitas = receta(OrigenReceta.PROPIA, null, "Tortitas de avena y plátano", Visibilidad.PRIVADO, ana);
        tortitas.setCategoria(categoriaRepositorio.findById(CATEGORIA_POSTRES).orElseThrow());
        tortitas.anadirIngrediente(new RecetaIngrediente("Avena", "60 g"));
        guardar(tortitas);
        guardar(receta(OrigenReceta.PROPIA, null, "Pollo al curry de Bruno", Visibilidad.PRIVADO, bruno));

        assertThat(nombres(buscar("", null, null, "", null, ana)))
                .containsExactly("Chicken Couscous", "Teriyaki Chicken Casserole", "Tortitas de avena y plátano");
        // Texto normalizado: sin tildes ni mayúsculas
        assertThat(nombres(buscar("platano", null, null, "", null, ana))).containsExactly("Tortitas de avena y plátano");
        // La receta privada de Bruno solo la ve él
        assertThat(nombres(buscar("pollo", null, null, "", null, ana))).isEmpty();
        assertThat(nombres(buscar("pollo", null, null, "", null, bruno))).containsExactly("Pollo al curry de Bruno");
        assertThat(nombres(buscar("", CATEGORIA_POLLO, null, "", null, ana)))
                .containsExactly("Chicken Couscous", "Teriyaki Chicken Casserole");
        assertThat(nombres(buscar("", null, OrigenReceta.PROPIA, "", null, ana)))
                .containsExactly("Tortitas de avena y plátano");
        // Por el nombre original del ingrediente (sin distinguir mayúsculas)...
        assertThat(nombres(buscar("", null, null, "chicken", null, ana)))
                .containsExactly("Chicken Couscous", "Teriyaki Chicken Casserole");
        // ...o por el alimento emparejado
        assertThat(nombres(buscar("", null, null, "", pollo.getId(), ana))).containsExactly("Chicken Couscous");
        assertThat(buscar("", null, null, "chicken", null, ana).getTotalElements()).isEqualTo(2);
    }

    // ---------------------------------------------------------------------------------------------
    // Favoritas, borrados y uso de alimentos
    // ---------------------------------------------------------------------------------------------

    @Test
    void unaFavoritaEsUnicaPorUsuarioYRecetaYSeBorraConLaReceta() {
        Receta receta = recetaDeTheMealDb("52850", "Chicken Couscous",
                r -> r.anadirIngrediente(new RecetaIngrediente("Couscous", "200g")));
        favoritaRepositorio.saveAndFlush(new RecetaFavorita(ana, receta));

        assertThat(favoritaRepositorio.findByIdUsuarioIdOrderByCreadoEnDesc(ana.getId()))
                .singleElement().satisfies(favorita -> assertThat(favorita.getCreadoEn()).isNotNull());
        assertThatThrownBy(() -> jdbc.update("INSERT INTO receta_favorita (usuario_id, receta_id, creado_en) "
                + "VALUES (?, ?, now())", ana.getId(), receta.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        recetaRepositorio.deleteById(receta.getId());

        assertThat(favoritaRepositorio.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM receta_ingrediente", Integer.class)).isZero();
    }

    @Test
    void unAlimentoUsadoEnUnaRecetaNoSePuedeBorrarYConstaComoEnUso() {
        Alimento pollo = alimento("Pechuga de pollo", null);
        Alimento sinUsar = alimento("Brócoli", null);
        recetaDeTheMealDb("52850", "Chicken Couscous", receta -> {
            RecetaIngrediente ingrediente = new RecetaIngrediente("Chicken Breast", "200g");
            ingrediente.setAlimento(pollo);
            receta.anadirIngrediente(ingrediente);
        });

        assertThat(usosAlimento.estaEnUso(pollo.getId())).isTrue();
        assertThat(usosAlimento.estaEnUso(sinUsar.getId())).isFalse();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM alimento WHERE id = ?", pollo.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void borrarUnaCuentaBorraSusRecetasYFavoritasAunqueUsenSusAlimentosPrivados() {
        // Receta privada de Ana con un alimento privado suyo: al borrar la cuenta se borran los dos en cascada
        Alimento propio = alimento("Mi crema de cacahuete", ana);
        Receta suya = receta(OrigenReceta.PROPIA, null, "Tostadas de Ana", Visibilidad.PRIVADO, ana);
        RecetaIngrediente ingrediente = new RecetaIngrediente("Mi crema de cacahuete", "20 g");
        ingrediente.setAlimento(propio);
        suya.anadirIngrediente(ingrediente);
        guardar(suya);
        Receta publica = recetaDeTheMealDb("52850", "Chicken Couscous", r -> { });
        favoritaRepositorio.saveAndFlush(new RecetaFavorita(ana, publica));

        jdbc.update("DELETE FROM usuario WHERE id = ?", ana.getId());

        assertThat(recetaRepositorio.findAll()).extracting(Receta::getNombre).containsExactly("Chicken Couscous");
        assertThat(alimentoRepositorio.count()).isZero();
        assertThat(favoritaRepositorio.count()).isZero();
    }

    // ---------------------------------------------------------------------------------------------

    private org.springframework.data.domain.Page<Receta> buscar(String q, UUID categoriaId, OrigenReceta origen,
            String ingrediente, UUID alimentoId, Usuario usuario) {
        return recetaRepositorio.buscar(q, categoriaId, origen, ingrediente, alimentoId, usuario.getId(),
                PageRequest.of(0, 20, Sort.by("nombreNormalizado")));
    }

    private static List<String> nombres(org.springframework.data.domain.Page<Receta> pagina) {
        return pagina.map(Receta::getNombre).getContent();
    }

    private Receta recetaDeTheMealDb(String idMeal, String nombre, Consumer<Receta> configurar) {
        Receta receta = receta(OrigenReceta.THEMEALDB, idMeal, nombre, Visibilidad.PUBLICO, null);
        receta.setIdioma("en");
        receta.setCategoriaExterna("Chicken");
        receta.setArea("Moroccan");
        receta.setCategoria(categoriaRepositorio.findById(CATEGORIA_POLLO).orElseThrow());
        receta.setImagenUrl("https://www.themealdb.com/images/media/meals/qxytrx1511304021.jpg");
        configurar.accept(receta);
        return guardar(receta);
    }

    private static Receta receta(OrigenReceta origen, String referencia, String nombre, Visibilidad visibilidad,
            Usuario propietario) {
        Receta receta = new Receta();
        receta.setOrigen(origen);
        receta.setReferenciaExterna(referencia);
        receta.setNombre(nombre);
        receta.setVisibilidad(visibilidad);
        receta.setPropietario(propietario);
        return receta;
    }

    private Receta guardar(Receta receta) {
        return recetaRepositorio.saveAndFlush(receta);
    }

    private Alimento alimento(String nombre, Usuario propietario) {
        Alimento alimento = new Alimento();
        alimento.setNombre(nombre);
        alimento.setCategoria(categoriaRepositorio.findById(CATEGORIA_CARNES).orElseThrow());
        alimento.setKcal100(new BigDecimal("120"));
        alimento.setProteinas100(new BigDecimal("23"));
        alimento.setCarbohidratos100(BigDecimal.ZERO);
        alimento.setGrasas100(new BigDecimal("2.5"));
        alimento.setUnidadBase(UnidadBase.G);
        alimento.setFuente(propietario == null ? FuenteDato.ADMIN : FuenteDato.USUARIO);
        alimento.setVisibilidad(propietario == null ? Visibilidad.PUBLICO : Visibilidad.PRIVADO);
        alimento.setPropietario(propietario);
        return alimentoRepositorio.saveAndFlush(alimento);
    }
}
