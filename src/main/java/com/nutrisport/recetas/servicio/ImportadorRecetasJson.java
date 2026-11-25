package com.nutrisport.recetas.servicio;

import com.nutrisport.catalogo.dto.ResultadoImportacion;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.Categoria;
import com.nutrisport.catalogo.entidad.TipoCategoria;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.recetas.entidad.Dificultad;
import com.nutrisport.recetas.entidad.EstadoConversion;
import com.nutrisport.recetas.entidad.OrigenReceta;
import com.nutrisport.recetas.entidad.PasoReceta;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.entidad.RecetaIngrediente;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Importa recetas propias desde un JSON (las de desarrollo: {@code datos/recetas_desarrollo.json}). Cada
 * ingrediente referencia un alimento del catálogo por su nombre exacto y lleva sus gramos, así que queda
 * emparejado con conversión EXACTA. Las recetas son públicas, publicadas y sin propietario.
 * <p>
 * Una receta propia con el mismo nombre que otra ya existente no se vuelve a crear. Si un alimento o una
 * categoría no existen, la receta se crea igualmente (el ingrediente sin emparejar o sin categoría) y el error se
 * devuelve en el resultado.
 */
@Service
public class ImportadorRecetasJson {

    /** Una receta del JSON. */
    record RecetaJson(String nombre, String descripcion, String categoria, Short raciones, Short tiempoPreparacionMin,
            Dificultad dificultad, List<String> franjasAptas, List<String> etiquetas,
            List<IngredienteJson> ingredientes, List<String> pasos) {
    }

    /** Un ingrediente del JSON: nombre exacto del alimento del catálogo, gramos para todas las raciones y medida. */
    record IngredienteJson(String alimento, BigDecimal gramos, String medida) {
    }

    private static final TypeReference<List<RecetaJson>> LISTA = new TypeReference<>() {
    };

    private final RecetaRepositorio recetaRepositorio;
    private final AlimentoRepositorio alimentoRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final JsonMapper jsonMapper;
    private final CalculoNutricionalServicio calculo;

    public ImportadorRecetasJson(RecetaRepositorio recetaRepositorio, AlimentoRepositorio alimentoRepositorio,
            CategoriaRepositorio categoriaRepositorio, JsonMapper jsonMapper, CalculoNutricionalServicio calculo) {
        this.recetaRepositorio = recetaRepositorio;
        this.alimentoRepositorio = alimentoRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
        this.jsonMapper = jsonMapper;
        this.calculo = calculo;
    }

    @Transactional
    public ResultadoImportacion importar(InputStream entrada) {
        List<RecetaJson> recetas = jsonMapper.readValue(entrada, LISTA);
        List<String> existentes = recetaRepositorio.findAll().stream()
                .filter(receta -> receta.getOrigen() == OrigenReceta.PROPIA)
                .map(Receta::getNombre)
                .toList();
        List<ErrorCampo> errores = new ArrayList<>();
        List<UUID> creadas = new ArrayList<>();
        for (RecetaJson json : recetas) {
            if (existentes.contains(json.nombre())) {
                continue;
            }
            creadas.add(recetaRepositorio.saveAndFlush(receta(json, errores)).getId());
        }
        // Sus ingredientes ya están emparejados y en gramos exactos: el cálculo no llama a USDA
        creadas.forEach(calculo::calcular);
        return new ResultadoImportacion(creadas.size(), 0, errores);
    }

    private Receta receta(RecetaJson json, List<ErrorCampo> errores) {
        Receta receta = new Receta();
        receta.setOrigen(OrigenReceta.PROPIA);
        receta.setNombre(json.nombre());
        receta.setDescripcion(json.descripcion());
        receta.setIdioma("es");
        receta.setVisibilidad(Visibilidad.PUBLICO);
        receta.setPublicada(true);
        receta.setRaciones(json.raciones() != null ? json.raciones() : Receta.RACIONES_POR_DEFECTO);
        receta.setRacionesSupuestas(false);
        receta.setTiempoPreparacionMin(json.tiempoPreparacionMin());
        receta.setDificultad(json.dificultad());
        receta.setFranjasAptas(json.franjasAptas());
        receta.setEtiquetas(json.etiquetas());
        List<PasoReceta> pasos = new ArrayList<>();
        if (json.pasos() != null) {
            json.pasos().forEach(paso -> pasos.add(new PasoReceta(pasos.size() + 1, paso)));
        }
        receta.setPasos(pasos);

        Categoria categoria = json.categoria() == null ? null
                : categoriaRepositorio.findByTipoAndNombre(TipoCategoria.RECETA, json.categoria()).orElse(null);
        if (categoria == null) {
            errores.add(new ErrorCampo(json.nombre(), "No existe la categoría de receta " + json.categoria()));
        }
        receta.setCategoria(categoria);

        if (json.ingredientes() != null) {
            for (IngredienteJson ingrediente : json.ingredientes()) {
                RecetaIngrediente linea = new RecetaIngrediente(ingrediente.alimento(), ingrediente.medida());
                Alimento alimento = alimentoRepositorio
                        .findFirstByNombreAndVisibilidadAndActivoTrue(ingrediente.alimento(), Visibilidad.PUBLICO)
                        .orElse(null);
                if (alimento == null) {
                    errores.add(new ErrorCampo(json.nombre(), "No existe el alimento " + ingrediente.alimento()));
                } else {
                    linea.setAlimento(alimento);
                    linea.setCantidadGramos(ingrediente.gramos());
                    linea.setEstadoConversion(ingrediente.gramos() != null ? EstadoConversion.EXACTA
                            : EstadoConversion.SIN_DATOS);
                }
                receta.anadirIngrediente(linea);
            }
        }
        return receta;
    }
}
