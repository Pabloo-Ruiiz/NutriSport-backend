package com.nutrisport.admin.servicio;

import com.nutrisport.admin.dto.ActualizarMapeoRequest;
import com.nutrisport.admin.dto.MapeoIngredienteResponse;
import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.catalogo.entidad.MapeoIngrediente;
import com.nutrisport.catalogo.entidad.MetodoMapeo;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.catalogo.repositorio.AlimentoRepositorio;
import com.nutrisport.catalogo.repositorio.MapeoIngredienteRepositorio;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.dto.PaginaResponse;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.recetas.repositorio.RecetaRepositorio;
import com.nutrisport.recetas.servicio.CalculoNutricionalServicio;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cola de revisión de los emparejamientos de ingredientes (§7.10, Ampliación). Al fijar el alimento de un
 * emparejamiento (MANUAL, revisado) se recalculan las recetas de TheMealDB que usan ese nombre de ingrediente. Cada
 * cambio queda auditado.
 */
@Service
public class AdminMapeoServicio {

    static final int TAMANO_MAXIMO_PAGINA = 100;

    private final MapeoIngredienteRepositorio mapeoRepositorio;
    private final AlimentoRepositorio alimentoRepositorio;
    private final RecetaRepositorio recetaRepositorio;
    private final CalculoNutricionalServicio calculo;
    private final AuditoriaServicio auditoriaServicio;
    private final TransactionTemplate escritura;
    private final TransactionTemplate lectura;

    public AdminMapeoServicio(MapeoIngredienteRepositorio mapeoRepositorio, AlimentoRepositorio alimentoRepositorio,
            RecetaRepositorio recetaRepositorio, CalculoNutricionalServicio calculo, AuditoriaServicio auditoriaServicio,
            PlatformTransactionManager transacciones) {
        this.mapeoRepositorio = mapeoRepositorio;
        this.alimentoRepositorio = alimentoRepositorio;
        this.recetaRepositorio = recetaRepositorio;
        this.calculo = calculo;
        this.auditoriaServicio = auditoriaServicio;
        this.escritura = new TransactionTemplate(transacciones);
        this.lectura = new TransactionTemplate(transacciones);
        this.lectura.setReadOnly(true);
    }

    /** Emparejamientos, filtrados por revisado si se indica; por nombre. Como mucho 100 por página. */
    public PaginaResponse<MapeoIngredienteResponse> listar(Boolean revisado, Pageable paginacion) {
        Pageable pagina = PageRequest.of(paginacion.getPageNumber(),
                Math.min(paginacion.getPageSize(), TAMANO_MAXIMO_PAGINA), Sort.by("nombreNormalizado"));
        return lectura.execute(estado -> PaginaResponse.de(revisado == null
                ? mapeoRepositorio.findAll(pagina)
                : mapeoRepositorio.findByRevisado(revisado, pagina), mapeo -> respuesta(mapeo, null)));
    }

    /**
     * Fija el alimento equivalente (o ninguno) de un emparejamiento: queda MANUAL y revisado. Después recalcula las
     * recetas de TheMealDB con un ingrediente de ese nombre.
     *
     * @throws RecursoNoEncontradoException 404 si el emparejamiento no existe
     * @throws PeticionInvalidaException    400 si el alimento no es un alimento público activo
     */
    public MapeoIngredienteResponse actualizar(UUID id, ActualizarMapeoRequest peticion) {
        String nombre = escritura.execute(estado -> {
            MapeoIngrediente mapeo = mapeoRepositorio.findById(id)
                    .orElseThrow(() -> new RecursoNoEncontradoException("El emparejamiento no existe"));
            Alimento alimento = peticion.alimentoId() == null ? null : alimentoPublico(peticion.alimentoId());
            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("ingrediente", mapeo.getNombreNormalizado());
            detalle.put("alimentoAnterior", mapeo.getAlimento() == null ? null : mapeo.getAlimento().getId().toString());
            detalle.put("alimentoNuevo", alimento == null ? null : alimento.getId().toString());
            mapeo.setAlimento(alimento);
            mapeo.setMetodo(MetodoMapeo.MANUAL);
            mapeo.setConfianza(null);
            mapeo.setRevisado(true);
            auditoriaServicio.registrar("EMPAREJAR_INGREDIENTE", "mapeo_ingrediente", id.toString(), detalle);
            return mapeo.getNombreNormalizado();
        });

        List<UUID> recetas = lectura.execute(estado -> recetaRepositorio.conIngrediente(nombre));
        recetas.forEach(calculo::calcular);
        return lectura.execute(estado -> respuesta(mapeoRepositorio.findById(id).orElseThrow(), recetas.size()));
    }

    private Alimento alimentoPublico(UUID alimentoId) {
        return alimentoRepositorio.findById(alimentoId)
                .filter(alimento -> alimento.getVisibilidad() == Visibilidad.PUBLICO && alimento.isActivo())
                .orElseThrow(() -> new PeticionInvalidaException("La petición contiene datos no válidos",
                        List.of(new ErrorCampo("alimentoId", "No es un alimento público activo del catálogo"))));
    }

    private static MapeoIngredienteResponse respuesta(MapeoIngrediente mapeo, Integer recalculadas) {
        Alimento alimento = mapeo.getAlimento();
        return new MapeoIngredienteResponse(mapeo.getId(), mapeo.getNombreNormalizado(),
                alimento == null ? null : alimento.getId(), alimento == null ? null : alimento.getNombre(),
                mapeo.getMetodo(), mapeo.getConfianza(), mapeo.isRevisado(), recalculadas);
    }
}
