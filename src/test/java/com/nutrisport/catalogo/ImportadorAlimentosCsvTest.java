package com.nutrisport.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutrisport.IntegracionBase;
import com.nutrisport.catalogo.dto.ResultadoImportacion;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.Preparacion;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.servicio.ImportadorAlimentosCsv;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Importación de alimentos desde CSV: filas válidas, filas con error, nutrientes opcionales y upsert. */
class ImportadorAlimentosCsvTest extends IntegracionBase {

    /**
     * Línea 1: cabecera. Líneas 2–4: válidas (la 3 sin fibra). Línea 5: kcal negativas.
     */
    private static final String CSV = """
            nombre;marca;categoria;preparacion;kcal100;proteinas100;carbohidratos100;azucares100;grasas100;saturadas100;fibra100;sal100;unidad_base;porciones;alergenos_contiene;alergenos_trazas;contiene_lactosa;apto_vegano;apto_vegetariano;apto_pescetariano
            Plátano;;Frutas;CRUDO;87.5;1.2;20.0;15.0;0.3;0.1;2.6;0.0;G;unidad mediana:120|unidad pequeña:90;;;false;true;true;true
            Pechuga de pollo a la plancha;;Carnes;PLANCHA;156.4;31.0;0.0;0.0;3.6;1.0;;0.2;G;filete:100;;;false;false;false;false
            Pasta cocida;;Cereales y derivados;COCIDO;151.3;5.8;30.0;0.6;0.9;0.2;1.8;0.0;G;plato:200;GLUTEN;HUEVOS;false;true;true;true
            Alimento erróneo;;Frutas;CRUDO;-10;1.0;10.0;;0.1;;;;G;;;;false;true;true;true
            """;

    private final ImportadorAlimentosCsv importador;
    private final AlimentoRepositorio alimentoRepositorio;
    private final JdbcTemplate jdbc;

    @Autowired
    ImportadorAlimentosCsvTest(ImportadorAlimentosCsv importador, AlimentoRepositorio alimentoRepositorio,
            JdbcTemplate jdbc) {
        this.importador = importador;
        this.alimentoRepositorio = alimentoRepositorio;
        this.jdbc = jdbc;
    }

    @BeforeEach
    @AfterEach
    void vaciarTablas() {
        // Las categorías iniciales (R__categorias.sql) se conservan
        jdbc.execute("TRUNCATE alimento, usuario CASCADE");
    }

    @Test
    void importaLasFilasValidasEInformaDeLaErronea() {
        ResultadoImportacion resultado = importar();

        assertThat(resultado.creados()).isEqualTo(3);
        assertThat(resultado.actualizados()).isZero();
        assertThat(resultado.errores()).singleElement().satisfies(error -> {
            assertThat(error.campo()).isEqualTo("fila 5");
            assertThat(error.mensaje()).contains("kcal100");
        });
        assertThat(alimentoRepositorio.count()).isEqualTo(3);

        Alimento pasta = buscar("Pasta cocida", Preparacion.COCIDO);
        assertThat(pasta.getVisibilidad()).isEqualTo(Visibilidad.PUBLICO);
        assertThat(pasta.isValidado()).isFalse();
        assertThat(pasta.getFuente()).isEqualTo(FuenteDato.ADMIN);
        assertThat(pasta.getReferenciaExterna()).isEqualTo("DEV");
        assertThat(pasta.getAlergenosContiene()).containsExactly("GLUTEN");
        assertThat(pasta.getAlergenosTrazas()).containsExactly("HUEVOS");
    }

    @Test
    void unNutrienteSecundarioVacioSeGuardaComoNullYNoComoCero() {
        importar();

        Alimento pollo = buscar("Pechuga de pollo a la plancha", Preparacion.PLANCHA);
        assertThat(pollo.getFibra100()).isNull();
        assertThat(pollo.getSal100()).isEqualByComparingTo("0.2");
        assertThat(jdbc.queryForObject(
                "SELECT fibra100 IS NULL FROM alimento WHERE nombre = 'Pechuga de pollo a la plancha'", Boolean.class))
                .isTrue();
    }

    @Test
    void reimportarActualizaEnLugarDeDuplicar() {
        importar();

        ResultadoImportacion segunda = importar();

        assertThat(segunda.creados()).isZero();
        assertThat(segunda.actualizados()).isEqualTo(3);
        assertThat(segunda.errores()).hasSize(1);
        assertThat(alimentoRepositorio.count()).isEqualTo(3);
    }

    private ResultadoImportacion importar() {
        InputStream entrada = new ByteArrayInputStream(CSV.getBytes(StandardCharsets.UTF_8));
        return importador.importar(entrada, FuenteDato.ADMIN, "DEV", true);
    }

    private Alimento buscar(String nombre, Preparacion preparacion) {
        return alimentoRepositorio.buscarPublico(nombre, null, preparacion).orElseThrow();
    }
}
