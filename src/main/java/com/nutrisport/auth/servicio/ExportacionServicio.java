package com.nutrisport.auth.servicio;

import com.nutrisport.actividad.mapper.EntrenamientoMapper;
import com.nutrisport.actividad.repositorio.EntrenamientoRepositorio;
import com.nutrisport.asistente.repositorio.ConversacionIaRepositorio;
import com.nutrisport.asistente.repositorio.MensajeIaRepositorio;
import com.nutrisport.auth.dto.ExportacionDatos;
import com.nutrisport.auth.dto.ExportacionDatos.Analisis;
import com.nutrisport.auth.dto.ExportacionDatos.ComidaMenu;
import com.nutrisport.auth.dto.ExportacionDatos.Conversacion;
import com.nutrisport.auth.dto.ExportacionDatos.Cuenta;
import com.nutrisport.auth.dto.ExportacionDatos.ListaCompra;
import com.nutrisport.auth.dto.ExportacionDatos.Mensaje;
import com.nutrisport.auth.dto.ExportacionDatos.Menu;
import com.nutrisport.auth.dto.ExportacionDatos.ProductoCompra;
import com.nutrisport.auth.dto.ExportacionDatos.RecetaFavorita;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.comidas.dto.RegistroComidaResponse;
import com.nutrisport.comidas.entidad.RegistroComida;
import com.nutrisport.comidas.mapper.ComidaMapper;
import com.nutrisport.comidas.repositorio.AnalisisImagenRepositorio;
import com.nutrisport.comidas.repositorio.RegistroComidaRepositorio;
import com.nutrisport.compra.repositorio.ListaCompraRepositorio;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.comun.excepcion.AccesoDenegadoException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.menus.repositorio.MenuSemanalRepositorio;
import com.nutrisport.perfil.dto.ObjetivoDiarioResponse;
import com.nutrisport.perfil.entidad.ObjetivoDiario;
import com.nutrisport.perfil.mapper.PerfilMapper;
import com.nutrisport.perfil.repositorio.ObjetivoDiarioRepositorio;
import com.nutrisport.perfil.repositorio.PerfilDeportivoRepositorio;
import com.nutrisport.perfil.repositorio.PreferenciaAlimentariaRepositorio;
import com.nutrisport.recetas.repositorio.RecetaFavoritaRepositorio;
import com.nutrisport.seguimiento.dto.InsightResponse;
import com.nutrisport.seguimiento.dto.PesoResponse;
import com.nutrisport.seguimiento.repositorio.InsightRepositorio;
import com.nutrisport.seguimiento.repositorio.RegistroPesoRepositorio;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exportación de todos los datos del usuario autenticado en JSON (§8.3 y §7.1: derechos de acceso y portabilidad).
 * Exige el email verificado. Se puede exportar aunque el consentimiento de salud esté revocado (§8.3: al revocarlo se
 * ofrece exportar y eliminar la cuenta).
 * <p>
 * Todas las consultas filtran por el usuario. Las comidas, que son lo más voluminoso, se recorren por lotes de 500 y
 * sus alimentos (como el resto de colecciones) se cargan por lotes ({@code hibernate.default_batch_fetch_size}).
 * Nunca se exportan el hash de la contraseña, los tokens ni las fotografías.
 */
@Service
@Transactional(readOnly = true)
public class ExportacionServicio {

    static final int TAMANO_LOTE_COMIDAS = 500;

    private final UsuarioRepositorio usuarioRepositorio;
    private final PerfilDeportivoRepositorio perfilRepositorio;
    private final PreferenciaAlimentariaRepositorio preferenciaRepositorio;
    private final ObjetivoDiarioRepositorio objetivoRepositorio;
    private final RegistroComidaRepositorio registroRepositorio;
    private final EntrenamientoRepositorio entrenamientoRepositorio;
    private final RegistroPesoRepositorio pesoRepositorio;
    private final MenuSemanalRepositorio menuRepositorio;
    private final ListaCompraRepositorio listaRepositorio;
    private final RecetaFavoritaRepositorio favoritaRepositorio;
    private final ConversacionIaRepositorio conversacionRepositorio;
    private final MensajeIaRepositorio mensajeRepositorio;
    private final AnalisisImagenRepositorio analisisRepositorio;
    private final InsightRepositorio insightRepositorio;
    private final PerfilMapper perfilMapper;
    private final ComidaMapper comidaMapper;
    private final EntrenamientoMapper entrenamientoMapper;
    private final SeguridadUtils seguridadUtils;
    private final Clock reloj;

    public ExportacionServicio(UsuarioRepositorio usuarioRepositorio, PerfilDeportivoRepositorio perfilRepositorio,
            PreferenciaAlimentariaRepositorio preferenciaRepositorio, ObjetivoDiarioRepositorio objetivoRepositorio,
            RegistroComidaRepositorio registroRepositorio, EntrenamientoRepositorio entrenamientoRepositorio,
            RegistroPesoRepositorio pesoRepositorio, MenuSemanalRepositorio menuRepositorio,
            ListaCompraRepositorio listaRepositorio, RecetaFavoritaRepositorio favoritaRepositorio,
            ConversacionIaRepositorio conversacionRepositorio, MensajeIaRepositorio mensajeRepositorio,
            AnalisisImagenRepositorio analisisRepositorio, InsightRepositorio insightRepositorio,
            PerfilMapper perfilMapper, ComidaMapper comidaMapper, EntrenamientoMapper entrenamientoMapper,
            SeguridadUtils seguridadUtils, Clock reloj) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.perfilRepositorio = perfilRepositorio;
        this.preferenciaRepositorio = preferenciaRepositorio;
        this.objetivoRepositorio = objetivoRepositorio;
        this.registroRepositorio = registroRepositorio;
        this.entrenamientoRepositorio = entrenamientoRepositorio;
        this.pesoRepositorio = pesoRepositorio;
        this.menuRepositorio = menuRepositorio;
        this.listaRepositorio = listaRepositorio;
        this.favoritaRepositorio = favoritaRepositorio;
        this.conversacionRepositorio = conversacionRepositorio;
        this.mensajeRepositorio = mensajeRepositorio;
        this.analisisRepositorio = analisisRepositorio;
        this.insightRepositorio = insightRepositorio;
        this.perfilMapper = perfilMapper;
        this.comidaMapper = comidaMapper;
        this.entrenamientoMapper = entrenamientoMapper;
        this.seguridadUtils = seguridadUtils;
        this.reloj = reloj;
    }

    /**
     * @throws AccesoDenegadoException 403 EMAIL_NO_VERIFICADO si el usuario no ha verificado su email
     */
    public ExportacionDatos exportar() {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        Usuario usuario = usuarioRepositorio.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe"));
        if (!usuario.emailVerificado()) {
            throw new AccesoDenegadoException("EMAIL_NO_VERIFICADO",
                    "Verifica tu email para poder exportar tus datos");
        }
        return new ExportacionDatos(
                OffsetDateTime.now(reloj),
                cuenta(usuario),
                perfilRepositorio.findByUsuarioId(usuarioId).map(perfilMapper::aRespuesta).orElse(null),
                perfilMapper.aDtos(preferenciaRepositorio.findByUsuarioId(usuarioId)),
                objetivoRepositorio.findByUsuarioIdOrderByFechaAsc(usuarioId).stream()
                        .map(ExportacionServicio::objetivo).toList(),
                comidas(usuarioId),
                entrenamientoRepositorio.findByUsuarioIdOrderByFechaHoraAsc(usuarioId).stream()
                        .map(entrenamientoMapper::aRespuesta).toList(),
                pesoRepositorio.findByUsuarioIdOrderByFechaAsc(usuarioId).stream()
                        .map(peso -> new PesoResponse(peso.getId(), peso.getFecha(), peso.getPesoKg())).toList(),
                menus(usuarioId),
                listas(usuarioId),
                favoritaRepositorio.findByIdUsuarioIdOrderByCreadoEnDesc(usuarioId).stream()
                        .map(favorita -> new RecetaFavorita(favorita.getReceta().getId(),
                                favorita.getReceta().getNombre(), favorita.getCreadoEn()))
                        .toList(),
                conversaciones(usuarioId),
                analisis(usuarioId),
                insightRepositorio.findByUsuarioIdOrderByCreadoEnAsc(usuarioId).stream()
                        .map(insight -> new InsightResponse(insight.getId(), insight.getTipo(),
                                insight.getSeveridad(), insight.getTitulo(), insight.getTexto(),
                                insight.getPeriodoDesde(), insight.getPeriodoHasta(), insight.getCreadoEn()))
                        .toList());
    }

    // ---------------------------------------------------------------------------------------------

    private static Cuenta cuenta(Usuario usuario) {
        return new Cuenta(usuario.getId(), usuario.getNombre(), usuario.getEmail(), Set.copyOf(usuario.getRoles()),
                usuario.getEstado(), usuario.getEmailVerificadoEn(), usuario.getMayorEdadDeclaradaEn(),
                usuario.getConsentimientoSaludEn(), usuario.getConsentimientoSaludVersion(),
                usuario.getConsentimientoSaludRevocadoEn(), usuario.getUnidades(), usuario.isInsightsActivos(),
                usuario.getCreadoEn(), usuario.getUltimoAcceso());
    }

    private static ObjetivoDiarioResponse objetivo(ObjetivoDiario objetivo) {
        return new ObjetivoDiarioResponse(objetivo.getFecha(),
                new Nutrientes(objetivo.getKcalBase(), objetivo.getProteinaG(), objetivo.getCarbohidratosG(),
                        objetivo.getGrasaG()),
                objetivo.getKcalEntreno(),
                new Nutrientes(objetivo.getKcalTotal(), objetivo.getProteinaG(), objetivo.getCarbohidratosTotalG(),
                        objetivo.getGrasaG()));
    }

    /** Todas las comidas con sus alimentos, por lotes; sin avisos (dependen de las restricciones actuales). */
    private List<RegistroComidaResponse> comidas(UUID usuarioId) {
        List<RegistroComidaResponse> comidas = new ArrayList<>();
        Pageable lote = PageRequest.of(0, TAMANO_LOTE_COMIDAS);
        Slice<RegistroComida> pagina;
        do {
            pagina = registroRepositorio.lotesDelUsuario(usuarioId, lote);
            pagina.forEach(registro -> comidas.add(comidaMapper.aRespuesta(registro, List.of())));
            lote = pagina.nextPageable();
        } while (pagina.hasNext());
        return comidas;
    }

    private List<Menu> menus(UUID usuarioId) {
        return menuRepositorio.findByUsuarioIdOrderByFechaInicioAscIdAsc(usuarioId).stream()
                .map(menu -> new Menu(menu.getId(), menu.getNombre(), menu.getTipo(), menu.getFechaInicio(),
                        menu.getFechaFin(), menu.isFavorito(), menu.getEstado(), menu.isGeneradoAutomaticamente(),
                        menu.getExplicacion(), menu.getCreadoEn(),
                        menu.getComidas().stream()
                                .map(comida -> new ComidaMenu(comida.getFecha(), comida.getTipoComida(),
                                        comida.getReceta().getId(), comida.getReceta().getNombre(),
                                        comida.getRaciones(), comida.isBloqueada(), comida.isConsumida(),
                                        comida.getRegistroComida() == null ? null
                                                : comida.getRegistroComida().getId()))
                                .toList()))
                .toList();
    }

    private List<ListaCompra> listas(UUID usuarioId) {
        return listaRepositorio.findByUsuarioIdOrderByCreadoEnAsc(usuarioId).stream()
                .map(lista -> new ListaCompra(lista.getId(), lista.getNombre(), lista.getEstado(),
                        lista.getMenu() == null ? null : lista.getMenu().getId(), lista.getFechaDesde(),
                        lista.getFechaHasta(), lista.getCreadoEn(),
                        lista.getProductos().stream()
                                .map(producto -> new ProductoCompra(producto.getNombre(),
                                        producto.getAlimento() == null ? null : producto.getAlimento().getId(),
                                        producto.getCantidad(), producto.getUnidad(), producto.getSeccion(),
                                        producto.isComprado(), producto.isManual(), producto.getDesglose()))
                                .toList()))
                .toList();
    }

    private List<Conversacion> conversaciones(UUID usuarioId) {
        return conversacionRepositorio.findByUsuarioIdOrderByCreadoEnAsc(usuarioId).stream()
                .map(conversacion -> new Conversacion(conversacion.getId(), conversacion.getTitulo(),
                        conversacion.isArchivada(), conversacion.getUltimoMensajeEn(), conversacion.getCreadoEn(),
                        mensajeRepositorio.deConversacion(conversacion.getId()).stream()
                                .map(mensaje -> new Mensaje(mensaje.getRol(), mensaje.getContenido(),
                                        mensaje.getHerramientasUsadas(), mensaje.getTokensEntrada(),
                                        mensaje.getTokensSalida(), mensaje.getValoracion() == null ? null
                                                : mensaje.getValoracion().intValue(), mensaje.getCreadoEn()))
                                .toList()))
                .toList();
    }

    /** Metadatos y candidatos; nunca las claves de las imágenes ni la respuesta en bruto del proveedor. */
    private List<Analisis> analisis(UUID usuarioId) {
        return analisisRepositorio.findByUsuarioIdOrderByCreadoEnAsc(usuarioId).stream()
                .map(analisis -> new Analisis(analisis.getId(), analisis.getTipo(), analisis.getEstado(),
                        analisis.getTipoComida(), analisis.getDescripcionUsuario(), analisis.getProveedor(),
                        analisis.getModelo(), List.copyOf(analisis.getCandidatos()),
                        analisis.getRegistroComida() == null ? null : analisis.getRegistroComida().getId(),
                        analisis.getCreadoEn()))
                .toList();
    }
}
