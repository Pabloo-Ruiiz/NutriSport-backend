package com.nutrisport.catalogo.servicio;

import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.catalogo.dto.AlimentoRequest;
import com.nutrisport.catalogo.dto.AlimentoResponse;
import com.nutrisport.catalogo.dto.AlimentoResumenResponse;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.Categoria;
import com.nutrisport.catalogo.entidad.FuenteDato;
import com.nutrisport.catalogo.entidad.TipoCategoria;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.mapper.AlimentoMapper;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.CategoriaRepositorio;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.dto.PaginaResponse;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.comun.util.TextoNormalizado;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de alimentos (§3.C y §7.3): búsqueda, detalle y alimentos privados del usuario. Un usuario
 * ve los alimentos públicos y los suyos; los privados de otro responden 404, nunca 403.
 */
@Service
@Transactional
public class AlimentoServicio {

    static final int LONGITUD_MINIMA_BUSQUEDA = 2;
    static final int LONGITUD_MAXIMA_BUSQUEDA = 60;
    static final int TAMANO_MAXIMO_PAGINA = 50;
    static final int LIMITE_RECIENTES_FRECUENTES = 20;
    static final int DIAS_FRECUENTES = 90;

    private static final String DATOS_NO_VALIDOS = "La petición contiene datos no válidos";
    private static final String NO_ENCONTRADO = "El alimento no existe";

    private final AlimentoRepositorio alimentoRepositorio;
    private final CategoriaRepositorio categoriaRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final SeguridadUtils seguridadUtils;
    private final AlimentoMapper alimentoMapper;
    private final UsoAlimento usoAlimento;
    private final Clock reloj;

    public AlimentoServicio(AlimentoRepositorio alimentoRepositorio, CategoriaRepositorio categoriaRepositorio,
            UsuarioRepositorio usuarioRepositorio, SeguridadUtils seguridadUtils, AlimentoMapper alimentoMapper,
            UsoAlimento usoAlimento, Clock reloj) {
        this.alimentoRepositorio = alimentoRepositorio;
        this.categoriaRepositorio = categoriaRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.seguridadUtils = seguridadUtils;
        this.alimentoMapper = alimentoMapper;
        this.usoAlimento = usoAlimento;
        this.reloj = reloj;
    }

    /**
     * Busca entre los alimentos activos visibles para el usuario, tolerando tildes, mayúsculas y
     * errores de escritura. {@code q} es obligatorio (2–60 caracteres) y la página admite como mucho 50
     * elementos; el orden es siempre por parecido, así que se ignora el {@code sort} recibido.
     */
    @Transactional(readOnly = true)
    public PaginaResponse<AlimentoResumenResponse> buscar(String q, UUID categoriaId, Pageable paginacion) {
        String consulta = normalizarConsulta(q);
        if (paginacion.getPageSize() > TAMANO_MAXIMO_PAGINA) {
            throw new PeticionInvalidaException(DATOS_NO_VALIDOS, List.of(new ErrorCampo("size",
                    "El tamaño de página no puede superar " + TAMANO_MAXIMO_PAGINA)));
        }
        Pageable sinOrden = PageRequest.of(paginacion.getPageNumber(), paginacion.getPageSize());
        return PaginaResponse.de(
                alimentoRepositorio.buscar(consulta, categoriaId, seguridadUtils.usuarioActualId(), sinOrden),
                alimentoMapper::aResumen);
    }

    /** Los 20 últimos alimentos distintos que el usuario ha registrado en sus comidas, el más reciente primero. */
    @Transactional(readOnly = true)
    public List<AlimentoResumenResponse> recientes() {
        return alimentoRepositorio.recientes(seguridadUtils.usuarioActualId(), LIMITE_RECIENTES_FRECUENTES).stream()
                .map(alimentoMapper::aResumen)
                .toList();
    }

    /** Los 20 alimentos que el usuario más ha registrado en los últimos 90 días (hoy incluido). */
    @Transactional(readOnly = true)
    public List<AlimentoResumenResponse> frecuentes() {
        LocalDate desde = LocalDate.now(reloj).minusDays(DIAS_FRECUENTES - 1L);
        return alimentoRepositorio.frecuentes(seguridadUtils.usuarioActualId(), desde, LIMITE_RECIENTES_FRECUENTES)
                .stream()
                .map(alimentoMapper::aResumen)
                .toList();
    }

    /** Detalle de un alimento visible para el usuario; 404 si no existe o es privado de otro. */
    @Transactional(readOnly = true)
    public AlimentoResponse obtener(UUID id) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        Alimento alimento = alimentoRepositorio.findById(id)
                .filter(encontrado -> encontrado.esVisiblePara(usuarioId))
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
        return alimentoMapper.aRespuesta(alimento, usuarioId);
    }

    /** Crea un alimento privado del usuario, pendiente de validar y con fuente USUARIO. */
    public AlimentoResponse crearPrivado(AlimentoRequest peticion) {
        validar(peticion);
        UUID usuarioId = seguridadUtils.usuarioActualId();
        Alimento alimento = new Alimento();
        alimentoMapper.actualizar(peticion, alimento);
        alimento.setCategoria(categoriaDeAlimento(peticion.categoriaId()));
        alimento.setFuente(FuenteDato.USUARIO);
        alimento.setVisibilidad(Visibilidad.PRIVADO);
        alimento.setPropietario(usuarioRepositorio.getReferenceById(usuarioId));
        alimento.setValidado(false);
        alimento.setAlergenosRevisados(false);
        return alimentoMapper.aRespuesta(alimentoRepositorio.save(alimento), usuarioId);
    }

    /** Edita un alimento privado propio y activo; cualquier otro responde 404. */
    public AlimentoResponse editar(UUID id, AlimentoRequest peticion) {
        validar(peticion);
        UUID usuarioId = seguridadUtils.usuarioActualId();
        Alimento alimento = privadoPropioActivo(id, usuarioId);
        alimentoMapper.actualizar(peticion, alimento);
        alimento.setCategoria(categoriaDeAlimento(peticion.categoriaId()));
        return alimentoMapper.aRespuesta(alimento, usuarioId);
    }

    /**
     * Elimina un alimento privado propio: si ya se ha usado solo se desactiva (no aparece en las
     * búsquedas pero el historial lo conserva); si no, se borra. Cualquier otro responde 404.
     */
    public void eliminar(UUID id) {
        Alimento alimento = privadoPropioActivo(id, seguridadUtils.usuarioActualId());
        if (usoAlimento.estaEnUso(alimento.getId())) {
            alimento.setActivo(false);
        } else {
            alimentoRepositorio.delete(alimento);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    private Alimento privadoPropioActivo(UUID id, UUID usuarioId) {
        return alimentoRepositorio.findById(id)
                .filter(alimento -> alimento.esPrivadoDe(usuarioId) && alimento.isActivo())
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
    }

    private Categoria categoriaDeAlimento(UUID categoriaId) {
        return categoriaRepositorio.findById(categoriaId)
                .filter(categoria -> categoria.getTipo() == TipoCategoria.ALIMENTO)
                .orElseThrow(() -> new PeticionInvalidaException(DATOS_NO_VALIDOS,
                        List.of(new ErrorCampo("categoriaId", "No es una categoría de alimentos"))));
    }

    /**
     * Normaliza la consulta igual que nombre_normalizado y quita los comodines de LIKE ({@code %},
     * {@code _} y {@code \}) para que se busquen como texto y no como patrón.
     */
    private static String normalizarConsulta(String q) {
        String consulta = TextoNormalizado.normalizar(q == null ? "" : q.replaceAll("[%_\\\\]", " "));
        if (consulta.length() < LONGITUD_MINIMA_BUSQUEDA || consulta.length() > LONGITUD_MAXIMA_BUSQUEDA) {
            throw new PeticionInvalidaException(DATOS_NO_VALIDOS, List.of(new ErrorCampo("q",
                    "La búsqueda debe tener entre " + LONGITUD_MINIMA_BUSQUEDA + " y " + LONGITUD_MAXIMA_BUSQUEDA
                            + " caracteres")));
        }
        return consulta;
    }

    /** Un alimento vegano es también vegetariano, y uno vegetariano es también pescetariano. */
    private static void validar(AlimentoRequest peticion) {
        List<ErrorCampo> errores = new ArrayList<>();
        if (peticion.aptoVegano() && !peticion.aptoVegetariano()) {
            errores.add(new ErrorCampo("aptoVegetariano", "Un alimento apto para veganos también lo es para vegetarianos"));
        }
        if (peticion.aptoVegetariano() && !peticion.aptoPescetariano()) {
            errores.add(new ErrorCampo("aptoPescetariano",
                    "Un alimento apto para vegetarianos también lo es para pescetarianos"));
        }
        if (!errores.isEmpty()) {
            throw new PeticionInvalidaException(DATOS_NO_VALIDOS, errores);
        }
    }
}
