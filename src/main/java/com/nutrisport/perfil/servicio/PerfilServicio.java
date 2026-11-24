package com.nutrisport.perfil.servicio;

import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.perfil.dto.PerfilRequest;
import com.nutrisport.perfil.dto.PerfilResponse;
import com.nutrisport.perfil.dto.PreferenciaDto;
import com.nutrisport.perfil.dto.PreferenciasRequest;
import com.nutrisport.perfil.entidad.Dieta;
import com.nutrisport.perfil.entidad.ObjetivoDeportivo;
import com.nutrisport.perfil.entidad.PerfilDeportivo;
import com.nutrisport.perfil.entidad.PreferenciaAlimentaria;
import com.nutrisport.perfil.entidad.Severidad;
import com.nutrisport.perfil.mapper.PerfilMapper;
import com.nutrisport.perfil.repositorio.PerfilDeportivoRepositorio;
import com.nutrisport.perfil.repositorio.PreferenciaAlimentariaRepositorio;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Perfil deportivo y preferencias alimentarias del usuario autenticado (§3.B y §7.2). Todas las
 * operaciones actúan sobre el usuario del token, nunca sobre un id recibido. El cálculo de los
 * objetivos nutricionales se añade en el paso 12.
 */
@Service
@Transactional
public class PerfilServicio {

    static final String PERFIL_NO_ENCONTRADO = "PERFIL_NO_ENCONTRADO";
    private static final String DATOS_NO_VALIDOS = "La petición contiene datos no válidos";

    private final PerfilDeportivoRepositorio perfilRepositorio;
    private final PreferenciaAlimentariaRepositorio preferenciaRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final SeguridadUtils seguridadUtils;
    private final PerfilMapper perfilMapper;

    public PerfilServicio(PerfilDeportivoRepositorio perfilRepositorio,
            PreferenciaAlimentariaRepositorio preferenciaRepositorio, UsuarioRepositorio usuarioRepositorio,
            SeguridadUtils seguridadUtils, PerfilMapper perfilMapper) {
        this.perfilRepositorio = perfilRepositorio;
        this.preferenciaRepositorio = preferenciaRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.seguridadUtils = seguridadUtils;
        this.perfilMapper = perfilMapper;
    }

    /** Perfil del usuario; 404 {@code PERFIL_NO_ENCONTRADO} si aún no lo ha creado. */
    @Transactional(readOnly = true)
    public PerfilResponse obtener() {
        PerfilDeportivo perfil = perfilRepositorio.findByUsuarioId(seguridadUtils.usuarioActualId())
                .orElseThrow(() -> new RecursoNoEncontradoException(PERFIL_NO_ENCONTRADO,
                        "Todavía no has completado tu perfil deportivo"));
        return perfilMapper.aRespuesta(perfil);
    }

    /** Crea el perfil si no existe o lo actualiza si ya existe (upsert). */
    public PerfilResponse guardar(PerfilRequest peticion) {
        validarFechaCompeticion(peticion);
        UUID usuarioId = seguridadUtils.usuarioActualId();
        PerfilDeportivo perfil = perfilRepositorio.findByUsuarioId(usuarioId)
                .orElseGet(() -> nuevoPerfil(usuarioId));
        perfilMapper.actualizar(peticion, perfil);
        return perfilMapper.aRespuesta(perfilRepositorio.save(perfil));
    }

    @Transactional(readOnly = true)
    public List<PreferenciaDto> obtenerPreferencias() {
        return perfilMapper.aDtos(preferenciaRepositorio.findByUsuarioId(seguridadUtils.usuarioActualId()));
    }

    /** Sustituye la lista completa: borra las preferencias actuales y crea las recibidas. */
    public List<PreferenciaDto> reemplazarPreferencias(PreferenciasRequest peticion) {
        validarPreferencias(peticion.preferencias());
        UUID usuarioId = seguridadUtils.usuarioActualId();
        preferenciaRepositorio.deleteByUsuarioId(usuarioId);
        // El borrado masivo limpia el contexto de persistencia: la referencia se obtiene después
        Usuario usuario = usuarioRepositorio.getReferenceById(usuarioId);
        List<PreferenciaAlimentaria> nuevas = peticion.preferencias().stream()
                .map(dto -> {
                    PreferenciaAlimentaria preferencia = perfilMapper.aEntidad(dto);
                    preferencia.setUsuario(usuario);
                    return preferencia;
                })
                .toList();
        return perfilMapper.aDtos(preferenciaRepositorio.saveAll(nuevas));
    }

    private PerfilDeportivo nuevoPerfil(UUID usuarioId) {
        PerfilDeportivo perfil = new PerfilDeportivo();
        perfil.setUsuario(usuarioRepositorio.getReferenceById(usuarioId));
        return perfil;
    }

    private static void validarFechaCompeticion(PerfilRequest peticion) {
        if (peticion.objetivo() == ObjetivoDeportivo.COMPETICION && peticion.fechaCompeticion() == null) {
            throw new PeticionInvalidaException(DATOS_NO_VALIDOS, List.of(new ErrorCampo("fechaCompeticion",
                    "Es obligatoria si el objetivo es preparar una competición")));
        }
    }

    /**
     * Cada preferencia debe llevar el dato que corresponde a su tipo y ninguno de los otros (CHECK
     * ck_preferencia_alimentaria_coherencia de V3). Halal y kosher solo se admiten como preferencia
     * blanda (§3.B).
     */
    private static void validarPreferencias(List<PreferenciaDto> preferencias) {
        List<ErrorCampo> errores = new ArrayList<>();
        for (int i = 0; i < preferencias.size(); i++) {
            PreferenciaDto p = preferencias.get(i);
            String prefijo = "preferencias[" + i + "].";
            boolean conAlergeno = p.alergeno() != null;
            boolean conIntolerancia = p.intolerancia() != null;
            boolean conDieta = p.dieta() != null;
            boolean conAlimentoOCategoria = p.alimentoId() != null || p.categoriaId() != null;
            switch (p.tipo()) {
                case ALERGIA -> {
                    exigir(conAlergeno, prefijo + "alergeno", "Es obligatorio en una alergia", errores);
                    prohibir(conIntolerancia || conDieta || conAlimentoOCategoria, prefijo,
                            "Una alergia solo admite el alérgeno", errores);
                }
                case INTOLERANCIA -> {
                    exigir(conIntolerancia, prefijo + "intolerancia", "Es obligatoria en una intolerancia", errores);
                    prohibir(conAlergeno || conDieta || conAlimentoOCategoria, prefijo,
                            "Una intolerancia solo admite la intolerancia", errores);
                }
                case DIETA -> {
                    exigir(conDieta, prefijo + "dieta", "Es obligatoria en una dieta", errores);
                    prohibir(conAlergeno || conIntolerancia || conAlimentoOCategoria, prefijo,
                            "Una dieta solo admite la dieta", errores);
                    if ((p.dieta() == Dieta.HALAL || p.dieta() == Dieta.KOSHER)
                            && p.severidad() == Severidad.ESTRICTA) {
                        errores.add(new ErrorCampo(prefijo + "severidad",
                                "Halal y kosher solo se admiten como PREFERENCIA"));
                    }
                }
                case RECHAZO, FAVORITO -> {
                    exigir(conAlimentoOCategoria, prefijo + "alimentoId", "Indica un alimento o una categoría",
                            errores);
                    prohibir(conAlergeno || conIntolerancia || conDieta, prefijo,
                            "Un rechazo o favorito solo admite alimento o categoría", errores);
                }
            }
        }
        if (!errores.isEmpty()) {
            throw new PeticionInvalidaException(DATOS_NO_VALIDOS, errores);
        }
    }

    private static void exigir(boolean presente, String campo, String mensaje, List<ErrorCampo> errores) {
        if (!presente) {
            errores.add(new ErrorCampo(campo, mensaje));
        }
    }

    private static void prohibir(boolean sobrante, String prefijo, String mensaje, List<ErrorCampo> errores) {
        if (sobrante) {
            errores.add(new ErrorCampo(prefijo + "tipo", mensaje));
        }
    }
}
