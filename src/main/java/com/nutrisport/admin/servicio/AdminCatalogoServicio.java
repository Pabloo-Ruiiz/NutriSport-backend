package com.nutrisport.admin.servicio;

import com.nutrisport.admin.dto.CategoriaRequest;
import com.nutrisport.admin.dto.RevisionAlergenosRequest;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.catalogo.dto.AlimentoRequest;
import com.nutrisport.catalogo.dto.AlimentoResponse;
import com.nutrisport.catalogo.dto.CategoriaResponse;
import com.nutrisport.catalogo.dto.ResultadoImportacion;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.Categoria;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.TipoCategoria;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.mapper.AlimentoMapper;
import com.nutrisport.catalogo.mapper.CategoriaMapper;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.catalogo.servicio.AlimentoServicio;
import com.nutrisport.catalogo.servicio.ImportadorAlimentosCsv;
import com.nutrisport.catalogo.servicio.UsosAlimento;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.dto.PaginaResponse;
import com.nutrisport.comun.excepcion.ConflictoException;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.comun.util.TextoNormalizado;
import com.nutrisport.perfil.entidad.Alergeno;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Gestión del catálogo por un administrador (§3.I): alimentos públicos, revisión de alérgenos,
 * importación CSV y categorías. Cada acción queda auditada en su misma transacción.
 */
@Service
@Transactional
public class AdminCatalogoServicio {

    static final int TAMANO_MAXIMO_PAGINA = 100;
    private static final String DATOS_NO_VALIDOS = "La petición contiene datos no válidos";

    private final AlimentoRepositorio alimentoRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final AlimentoServicio alimentoServicio;
    private final ImportadorAlimentosCsv importador;
    private final UsosAlimento usoAlimento;
    private final AuditoriaServicio auditoriaServicio;
    private final SeguridadUtils seguridadUtils;
    private final AlimentoMapper alimentoMapper;
    private final CategoriaMapper categoriaMapper;

    public AdminCatalogoServicio(AlimentoRepositorio alimentoRepositorio, CategoriaRepositorio categoriaRepositorio,
            AlimentoServicio alimentoServicio, ImportadorAlimentosCsv importador, UsosAlimento usoAlimento,
            AuditoriaServicio auditoriaServicio, SeguridadUtils seguridadUtils, AlimentoMapper alimentoMapper,
            CategoriaMapper categoriaMapper) {
        this.alimentoRepositorio = alimentoRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
        this.alimentoServicio = alimentoServicio;
        this.importador = importador;
        this.usoAlimento = usoAlimento;
        this.auditoriaServicio = auditoriaServicio;
        this.seguridadUtils = seguridadUtils;
        this.alimentoMapper = alimentoMapper;
        this.categoriaMapper = categoriaMapper;
    }

    // ---------------------------------------------------------------------------------------------
    // Alimentos
    // ---------------------------------------------------------------------------------------------

    /** Todo el catálogo, incluidos los alimentos privados de usuarios y los desactivados, por nombre. */
    @Transactional(readOnly = true)
    public PaginaResponse<AlimentoResponse> buscar(String q, FuenteDato fuente, Boolean revisados,
            Visibilidad visibilidad, Boolean activo, Pageable paginacion) {
        String consulta = q == null || q.isBlank() ? "" : TextoNormalizado.paraBusqueda(q);
        Pageable pagina = PageRequest.of(paginacion.getPageNumber(),
                Math.min(paginacion.getPageSize(), TAMANO_MAXIMO_PAGINA), Sort.by("nombreNormalizado", "id"));
        UUID adminId = seguridadUtils.usuarioActualId();
        return PaginaResponse.de(
                alimentoRepositorio.buscarParaAdmin(consulta, fuente, revisados, visibilidad, activo, pagina),
                alimento -> alimentoMapper.aRespuesta(alimento, adminId));
    }

    /**
     * Crea un alimento público, ya validado. Los alérgenos quedan sin revisar hasta que se revisan
     * expresamente con {@link #revisarAlergenos}.
     */
    public AlimentoResponse crearAlimento(AlimentoRequest peticion, FuenteDato fuente, String referenciaExterna) {
        if (fuente == null) {
            throw invalida("fuente", "La fuente es obligatoria");
        }
        AlimentoServicio.validar(peticion);
        Alimento alimento = new Alimento();
        alimentoMapper.actualizar(peticion, alimento);
        alimento.setCategoria(alimentoServicio.categoriaDeAlimento(peticion.categoriaId()));
        alimento.setFuente(fuente);
        alimento.setReferenciaExterna(referenciaExterna == null || referenciaExterna.isBlank() ? null : referenciaExterna.strip());
        alimento.setVisibilidad(Visibilidad.PUBLICO);
        alimento.setPropietario(null);
        alimento.setValidado(true);
        alimento.setAlergenosRevisados(false);
        alimentoRepositorio.save(alimento);
        auditoriaServicio.registrar("CREAR_ALIMENTO", "alimento", alimento.getId().toString(),
                Map.of("nombre", alimento.getNombre(), "fuente", fuente.name()));
        return alimentoMapper.aRespuesta(alimento, seguridadUtils.usuarioActualId());
    }

    /**
     * Edita cualquier alimento (también los privados de usuarios). Si cambian sus alérgenos o la
     * lactosa, la revisión anterior deja de valer y vuelve a quedar sin revisar.
     */
    public AlimentoResponse editarAlimento(UUID id, AlimentoRequest peticion) {
        AlimentoServicio.validar(peticion);
        Alimento alimento = alimento(id);
        Set<String> contieneAntes = new HashSet<>(alimento.getAlergenosContiene());
        Set<String> trazasAntes = new HashSet<>(alimento.getAlergenosTrazas());
        boolean lactosaAntes = alimento.isContieneLactosa();
        alimentoMapper.actualizar(peticion, alimento);
        alimento.setCategoria(alimentoServicio.categoriaDeAlimento(peticion.categoriaId()));
        boolean alergenosCambiados = !contieneAntes.equals(new HashSet<>(alimento.getAlergenosContiene()))
                || !trazasAntes.equals(new HashSet<>(alimento.getAlergenosTrazas()))
                || lactosaAntes != alimento.isContieneLactosa();
        if (alergenosCambiados) {
            alimento.setAlergenosRevisados(false);
        }
        auditoriaServicio.registrar("EDITAR_ALIMENTO", "alimento", id.toString(),
                Map.of("nombre", alimento.getNombre(), "alergenosCambiados", alergenosCambiados));
        return alimentoMapper.aRespuesta(alimento, seguridadUtils.usuarioActualId());
    }

    /** Borra el alimento; si ya se usó en alguna comida, solo lo desactiva para no romper el historial. */
    public void eliminarAlimento(UUID id) {
        Alimento alimento = alimento(id);
        if (usoAlimento.estaEnUso(id)) {
            alimento.setActivo(false);
            auditoriaServicio.registrar("DESACTIVAR_ALIMENTO", "alimento", id.toString(),
                    Map.of("nombre", alimento.getNombre(), "motivo", "usado en comidas"));
        } else {
            auditoriaServicio.registrar("ELIMINAR_ALIMENTO", "alimento", id.toString(),
                    Map.of("nombre", alimento.getNombre()));
            alimentoRepositorio.delete(alimento);
        }
    }

    /** Sustituye los alérgenos, las trazas y la lactosa, y marca los alérgenos como revisados. */
    public AlimentoResponse revisarAlergenos(UUID id, RevisionAlergenosRequest peticion) {
        Alimento alimento = alimento(id);
        alimento.setAlergenosContiene(nombres(peticion.alergenosContiene()));
        alimento.setAlergenosTrazas(nombres(peticion.alergenosTrazas()));
        alimento.setContieneLactosa(peticion.contieneLactosa());
        alimento.setAlergenosRevisados(true);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("alergenosContiene", alimento.getAlergenosContiene());
        detalle.put("alergenosTrazas", alimento.getAlergenosTrazas());
        detalle.put("contieneLactosa", alimento.isContieneLactosa());
        auditoriaServicio.registrar("REVISAR_ALERGENOS", "alimento", id.toString(), detalle);
        return alimentoMapper.aRespuesta(alimento, seguridadUtils.usuarioActualId());
    }

    /**
     * Importa alimentos públicos desde CSV con alérgenos sin revisar. Las filas con error se devuelven en
     * el resultado y no detienen la importación (máximo 5000 filas).
     */
    public ResultadoImportacion importarCsv(MultipartFile archivo, FuenteDato fuente, String referencia) {
        if (archivo == null || archivo.isEmpty()) {
            throw invalida("archivo", "El fichero CSV es obligatorio");
        }
        if (fuente == null) {
            throw invalida("fuente", "La fuente es obligatoria");
        }
        String referenciaLimpia = referencia == null || referencia.isBlank() ? null : referencia.strip();
        ResultadoImportacion resultado;
        try (InputStream entrada = archivo.getInputStream()) {
            resultado = importador.importar(entrada, fuente, referenciaLimpia, false);
        } catch (IOException e) {
            throw new UncheckedIOException("No se ha podido leer el fichero CSV", e);
        }
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("fichero", archivo.getOriginalFilename());
        detalle.put("fuente", fuente.name());
        detalle.put("creados", resultado.creados());
        detalle.put("actualizados", resultado.actualizados());
        detalle.put("errores", resultado.errores().size());
        auditoriaServicio.registrar("IMPORTAR_ALIMENTOS", "alimento", null, detalle);
        return resultado;
    }

    // ---------------------------------------------------------------------------------------------
    // Categorías
    // ---------------------------------------------------------------------------------------------

    public CategoriaResponse crearCategoria(CategoriaRequest peticion) {
        if (categoriaRepositorio.existsByTipoAndNombreIgnoreCase(peticion.tipo(), peticion.nombre().strip())) {
            throw new ConflictoException("Ya existe una categoría de " + peticion.tipo() + " con ese nombre");
        }
        Categoria categoria = new Categoria();
        aplicar(peticion, categoria);
        categoriaRepositorio.save(categoria);
        auditoriaServicio.registrar("CREAR_CATEGORIA", "categoria", categoria.getId().toString(),
                Map.of("nombre", categoria.getNombre(), "tipo", categoria.getTipo().name()));
        return categoriaMapper.aRespuesta(categoria);
    }

    public CategoriaResponse editarCategoria(UUID id, CategoriaRequest peticion) {
        Categoria categoria = categoria(id);
        if (categoriaRepositorio.existsByTipoAndNombreIgnoreCaseAndIdNot(peticion.tipo(), peticion.nombre().strip(), id)) {
            throw new ConflictoException("Ya existe una categoría de " + peticion.tipo() + " con ese nombre");
        }
        if (categoria.getTipo() != peticion.tipo()
                && (alimentoRepositorio.existsByCategoriaId(id) || categoriaRepositorio.existsByPadreId(id))) {
            throw new ConflictoException("No se puede cambiar el tipo de una categoría con elementos");
        }
        aplicar(peticion, categoria);
        auditoriaServicio.registrar("EDITAR_CATEGORIA", "categoria", id.toString(),
                Map.of("nombre", categoria.getNombre(), "tipo", categoria.getTipo().name()));
        return categoriaMapper.aRespuesta(categoria);
    }

    /** Borra la categoría solo si no tiene alimentos ni subcategorías; si no, 409. */
    public void eliminarCategoria(UUID id) {
        Categoria categoria = categoria(id);
        if (alimentoRepositorio.existsByCategoriaId(id) || categoriaRepositorio.existsByPadreId(id)) {
            throw new ConflictoException("La categoría tiene alimentos o subcategorías y no se puede borrar");
        }
        auditoriaServicio.registrar("ELIMINAR_CATEGORIA", "categoria", id.toString(),
                Map.of("nombre", categoria.getNombre(), "tipo", categoria.getTipo().name()));
        categoriaRepositorio.delete(categoria);
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    /** Copia la petición validando el padre (mismo tipo, sin ciclos) y la sección (solo en ALIMENTO). */
    private void aplicar(CategoriaRequest peticion, Categoria categoria) {
        String seccion = peticion.seccionCompra() == null || peticion.seccionCompra().isBlank()
                ? null : peticion.seccionCompra().strip();
        if (seccion != null && peticion.tipo() != TipoCategoria.ALIMENTO) {
            throw invalida("seccionCompra", "Solo las categorías de alimentos tienen sección de la compra");
        }
        Categoria padre = null;
        if (peticion.padreId() != null) {
            padre = categoriaRepositorio.findById(peticion.padreId())
                    .orElseThrow(() -> invalida("padreId", "La categoría padre no existe"));
            if (padre.getTipo() != peticion.tipo()) {
                throw invalida("padreId", "La categoría padre debe ser del mismo tipo");
            }
            for (Categoria ancestro = padre; ancestro != null; ancestro = ancestro.getPadre()) {
                if (categoria.getId() != null && categoria.getId().equals(ancestro.getId())) {
                    throw invalida("padreId", "Una categoría no puede estar dentro de sí misma");
                }
            }
        }
        categoria.setNombre(peticion.nombre().strip());
        categoria.setTipo(peticion.tipo());
        categoria.setPadre(padre);
        categoria.setSeccionCompra(seccion);
        categoria.setOrden(peticion.orden().shortValue());
    }

    private Alimento alimento(UUID id) {
        return alimentoRepositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El alimento no existe"));
    }

    private Categoria categoria(UUID id) {
        return categoriaRepositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("La categoría no existe"));
    }

    private static List<String> nombres(Set<Alergeno> alergenos) {
        return alergenos.stream().map(Alergeno::name).sorted().toList();
    }

    private static PeticionInvalidaException invalida(String campo, String mensaje) {
        return new PeticionInvalidaException(DATOS_NO_VALIDOS, List.of(new ErrorCampo(campo, mensaje)));
    }
}
