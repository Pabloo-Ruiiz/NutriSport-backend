package com.nutrisport.admin.servicio;

import com.nutrisport.admin.dto.IngredienteRecetaAdminRequest;
import com.nutrisport.admin.dto.PublicarRecetaRequest;
import com.nutrisport.admin.dto.RecetaAdminRequest;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.Categoria;
import com.nutrisport.catalogo.entidad.TipoCategoria;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.excepcion.ConflictoException;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.menus.repositorio.ComidaPlanificadaRepositorio;
import com.nutrisport.recetas.dto.RecetaResponse;
import com.nutrisport.recetas.entidad.EstadoConversion;
import com.nutrisport.recetas.entidad.OrigenReceta;
import com.nutrisport.recetas.entidad.PasoReceta;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.entidad.RecetaIngrediente;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import com.nutrisport.recetas.servicio.CalculoNutricionalServicio;
import com.nutrisport.recetas.servicio.RecetaServicio;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gestión de recetas por un administrador (§7.10, Ampliación): recetas propias públicas (crear, editar y borrar) y
 * publicación de cualquier receta pública, también las importadas de TheMealDB. Cada acción queda auditada y, cuando
 * cambian los ingredientes o las raciones, se recalculan los nutrientes (fuera de la transacción: puede llamar a
 * USDA).
 */
@Service
public class AdminRecetaServicio {

    private static final String DATOS_NO_VALIDOS = "La petición contiene datos no válidos";
    private static final String NO_ENCONTRADA = "La receta no existe";

    private final RecetaRepositorio recetaRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final AlimentoRepositorio alimentoRepositorio;
    private final CalculoNutricionalServicio calculo;
    private final RecetaServicio recetaServicio;
    private final AuditoriaServicio auditoriaServicio;
    private final ComidaPlanificadaRepositorio comidaPlanificadaRepositorio;
    private final TransactionTemplate escritura;

    public AdminRecetaServicio(RecetaRepositorio recetaRepositorio, CategoriaRepositorio categoriaRepositorio,
            AlimentoRepositorio alimentoRepositorio, CalculoNutricionalServicio calculo, RecetaServicio recetaServicio,
            AuditoriaServicio auditoriaServicio, ComidaPlanificadaRepositorio comidaPlanificadaRepositorio,
            PlatformTransactionManager transacciones) {
        this.recetaRepositorio = recetaRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
        this.alimentoRepositorio = alimentoRepositorio;
        this.calculo = calculo;
        this.recetaServicio = recetaServicio;
        this.auditoriaServicio = auditoriaServicio;
        this.comidaPlanificadaRepositorio = comidaPlanificadaRepositorio;
        this.escritura = new TransactionTemplate(transacciones);
    }

    /** Crea una receta propia pública, sin publicar y con las raciones reales, y calcula sus nutrientes. */
    public RecetaResponse crear(RecetaAdminRequest peticion) {
        UUID id = escritura.execute(estado -> {
            Receta receta = new Receta();
            receta.setOrigen(OrigenReceta.PROPIA);
            receta.setVisibilidad(Visibilidad.PUBLICO);
            receta.setPropietario(null);
            receta.setPublicada(false);
            receta.setIdioma("es");
            aplicar(peticion, receta);
            UUID nueva = recetaRepositorio.saveAndFlush(receta).getId();
            auditoriaServicio.registrar("CREAR_RECETA", "receta", nueva.toString(), Map.of("nombre", receta.getNombre()));
            return nueva;
        });
        calculo.calcular(id);
        return recetaServicio.detalle(id, null);
    }

    /** Sustituye los datos y los ingredientes de una receta propia pública y la recalcula. 404 si no lo es. */
    public RecetaResponse editar(UUID id, RecetaAdminRequest peticion) {
        escritura.executeWithoutResult(estado -> {
            Receta receta = propiaPublica(id);
            receta.vaciarIngredientes();
            recetaRepositorio.flush();
            aplicar(peticion, receta);
            auditoriaServicio.registrar("EDITAR_RECETA", "receta", id.toString(), Map.of("nombre", receta.getNombre()));
        });
        calculo.calcular(id);
        return recetaServicio.detalle(id, null);
    }

    /**
     * Borra una receta propia pública (y sus favoritas). 404 si no lo es; 409 RECETA_PLANIFICADA si está en algún
     * menú (la FK lo impediría al confirmar la transacción).
     */
    public void eliminar(UUID id) {
        escritura.executeWithoutResult(estado -> {
            Receta receta = propiaPublica(id);
            if (comidaPlanificadaRepositorio.recetaPlanificada(id)) {
                throw new ConflictoException("RECETA_PLANIFICADA",
                        "La receta está en algún menú: despublícala en lugar de borrarla");
            }
            auditoriaServicio.registrar("ELIMINAR_RECETA", "receta", id.toString(), Map.of("nombre", receta.getNombre()));
            recetaRepositorio.delete(receta);
        });
    }

    /**
     * Publica o despublica una receta pública (propia o de TheMealDB) y fija sus franjas o sus raciones reales (dejan
     * de ser supuestas y se recalculan los nutrientes por ración). Los campos nulos no cambian. 404 si no es pública.
     */
    public RecetaResponse publicacion(UUID id, PublicarRecetaRequest peticion) {
        boolean recalcular = escritura.execute(estado -> {
            Receta receta = recetaRepositorio.findById(id)
                    .filter(r -> r.getVisibilidad() == Visibilidad.PUBLICO)
                    .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA));
            Map<String, Object> detalle = new LinkedHashMap<>();
            if (peticion.publicada() != null) {
                detalle.put("publicada", peticion.publicada());
                receta.setPublicada(peticion.publicada());
            }
            if (peticion.franjasAptas() != null) {
                List<String> franjas = peticion.franjasAptas().stream().map(Enum::name).sorted().toList();
                detalle.put("franjasAptas", franjas);
                receta.setFranjasAptas(franjas);
            }
            boolean racionesCambian = peticion.raciones() != null;
            if (racionesCambian) {
                detalle.put("racionesAnteriores", receta.getRaciones());
                detalle.put("raciones", peticion.raciones());
                receta.setRaciones(peticion.raciones().shortValue());
                receta.setRacionesSupuestas(false);
            }
            auditoriaServicio.registrar("PUBLICAR_RECETA", "receta", id.toString(), detalle);
            return racionesCambian;
        });
        if (recalcular) {
            calculo.calcular(id);
        }
        return recetaServicio.detalle(id, null);
    }

    // ---------------------------------------------------------------------------------------------

    private Receta propiaPublica(UUID id) {
        return recetaRepositorio.findWithIngredientesById(id)
                .filter(r -> r.getOrigen() == OrigenReceta.PROPIA && r.getVisibilidad() == Visibilidad.PUBLICO
                        && r.getPropietario() == null)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA));
    }

    /** Datos de la petición en la receta, comprobando la categoría y los alimentos (400 si no valen). */
    private void aplicar(RecetaAdminRequest peticion, Receta receta) {
        List<ErrorCampo> errores = new ArrayList<>();
        Categoria categoria = categoriaRepositorio.findById(peticion.categoriaId())
                .filter(c -> c.getTipo() == TipoCategoria.RECETA)
                .orElse(null);
        if (categoria == null) {
            errores.add(new ErrorCampo("categoriaId", "No es una categoría de recetas"));
        }
        Map<UUID, Alimento> alimentos = alimentoRepositorio.findAllById(peticion.ingredientes().stream()
                        .map(IngredienteRecetaAdminRequest::alimentoId).toList()).stream()
                .filter(a -> a.getVisibilidad() == Visibilidad.PUBLICO && a.isActivo())
                .collect(Collectors.toMap(Alimento::getId, a -> a));
        for (int i = 0; i < peticion.ingredientes().size(); i++) {
            if (!alimentos.containsKey(peticion.ingredientes().get(i).alimentoId())) {
                errores.add(new ErrorCampo("ingredientes[" + i + "].alimentoId",
                        "No es un alimento público activo del catálogo"));
            }
        }
        if (!errores.isEmpty()) {
            throw new PeticionInvalidaException(DATOS_NO_VALIDOS, errores);
        }

        receta.setNombre(peticion.nombre().strip());
        receta.setDescripcion(peticion.descripcion() == null || peticion.descripcion().isBlank() ? null
                : peticion.descripcion().strip());
        receta.setCategoria(categoria);
        receta.setRaciones(peticion.raciones().shortValue());
        receta.setRacionesSupuestas(false);
        receta.setTiempoPreparacionMin(peticion.tiempoPreparacionMin() == null ? null
                : peticion.tiempoPreparacionMin().shortValue());
        receta.setDificultad(peticion.dificultad());
        receta.setFranjasAptas(peticion.franjasAptas() == null ? List.of()
                : peticion.franjasAptas().stream().map(Enum::name).sorted().toList());
        receta.setEtiquetas(peticion.etiquetas() == null ? List.of()
                : peticion.etiquetas().stream().map(String::strip).toList());
        List<PasoReceta> pasos = new ArrayList<>();
        if (peticion.pasos() != null) {
            peticion.pasos().forEach(paso -> pasos.add(new PasoReceta(pasos.size() + 1, paso.strip())));
        }
        receta.setPasos(pasos);
        for (IngredienteRecetaAdminRequest ingrediente : peticion.ingredientes()) {
            Alimento alimento = alimentos.get(ingrediente.alimentoId());
            String medida = ingrediente.medida() != null && !ingrediente.medida().isBlank()
                    ? ingrediente.medida().strip()
                    : ingrediente.gramos().stripTrailingZeros().toPlainString() + " g";
            RecetaIngrediente linea = new RecetaIngrediente(recortar(alimento.getNombre(), 100), medida);
            linea.setAlimento(alimento);
            linea.setCantidadGramos(ingrediente.gramos());
            linea.setEstadoConversion(ingrediente.gramos() != null ? EstadoConversion.EXACTA : EstadoConversion.SIN_DATOS);
            receta.anadirIngrediente(linea);
        }
    }

    private static String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
