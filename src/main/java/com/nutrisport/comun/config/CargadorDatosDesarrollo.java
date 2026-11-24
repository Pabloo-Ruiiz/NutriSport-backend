package com.nutrisport.comun.config;

import com.nutrisport.catalogo.dto.ResultadoImportacion;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.servicio.ImportadorAlimentosCsv;
import com.nutrisport.comun.dto.ErrorCampo;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Solo en el perfil {@code local}: si el catálogo está vacío, carga los alimentos genéricos de
 * {@code datos/alimentos_desarrollo.csv} para poder probar la aplicación. Nunca se ejecuta en
 * producción ni en los tests.
 */
@Component
@Profile("local")
public class CargadorDatosDesarrollo implements ApplicationRunner {

    static final String FICHERO = "datos/alimentos_desarrollo.csv";
    static final String REFERENCIA = "DEV";

    private static final Logger LOG = LoggerFactory.getLogger(CargadorDatosDesarrollo.class);

    private final AlimentoRepositorio alimentoRepositorio;
    private final ImportadorAlimentosCsv importador;

    public CargadorDatosDesarrollo(AlimentoRepositorio alimentoRepositorio, ImportadorAlimentosCsv importador) {
        this.alimentoRepositorio = alimentoRepositorio;
        this.importador = importador;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (alimentoRepositorio.count() > 0) {
            LOG.debug("El catálogo ya tiene alimentos: no se cargan los datos de desarrollo");
            return;
        }
        try (InputStream entrada = new ClassPathResource(FICHERO).getInputStream()) {
            // alergenosRevisados = true SOLO en local, para poder probar los flujos con alergias: en un
            // entorno real los alérgenos los revisa un administrador antes de darlos por buenos (§6.3),
            // y un alimento sin revisar se excluye de los menús de usuarios con alergias estrictas.
            ResultadoImportacion resultado = importador.importar(entrada, FuenteDato.ADMIN, REFERENCIA, true);
            LOG.info("Datos de desarrollo cargados desde {}: {} alimentos creados, {} actualizados, {} filas con errores",
                    FICHERO, resultado.creados(), resultado.actualizados(), resultado.errores().size());
            for (ErrorCampo error : resultado.errores()) {
                LOG.warn("{} ({}): {}", FICHERO, error.campo(), error.mensaje());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se ha podido leer " + FICHERO, e);
        }
    }
}
