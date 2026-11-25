package com.nutrisport.asistente.servicio;

import com.nutrisport.actividad.entidad.EstadoEntrenamiento;
import com.nutrisport.actividad.repositorio.TipoActividadRepositorio;
import com.nutrisport.actividad.servicio.EntrenamientoServicio;
import com.nutrisport.asistente.dto.ActualizarConversacionRequest;
import com.nutrisport.asistente.dto.ConversacionResponse;
import com.nutrisport.asistente.dto.ConversacionResumenResponse;
import com.nutrisport.asistente.dto.EnviarMensajeRequest;
import com.nutrisport.asistente.dto.MensajeResponse;
import com.nutrisport.asistente.entidad.ConversacionIa;
import com.nutrisport.asistente.entidad.MensajeIa;
import com.nutrisport.asistente.entidad.RolMensaje;
import com.nutrisport.asistente.repositorio.ConversacionIaRepositorio;
import com.nutrisport.asistente.repositorio.MensajeIaRepositorio;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.dto.PaginaResponse;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.comun.excepcion.ReglaNegocioException;
import com.nutrisport.ia.entidad.FuncionIa;
import com.nutrisport.ia.puerto.ChatAsistentePuerto;
import com.nutrisport.ia.puerto.ContextoChat;
import com.nutrisport.ia.puerto.RespuestaChat;
import com.nutrisport.ia.puerto.ResultadoIa;
import com.nutrisport.ia.servicio.LimiteIaServicio;
import com.nutrisport.ia.servicio.UsoIaServicio;
import com.nutrisport.perfil.entidad.PerfilDeportivo;
import com.nutrisport.perfil.repositorio.PerfilDeportivoRepositorio;
import com.nutrisport.perfil.servicio.RestriccionesServicio;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Conversaciones del usuario autenticado con el asistente (§3.H y §7.9, Ampliación). Las conversaciones y los
 * mensajes de otro usuario responden 404.
 * <p>
 * Enviar un mensaje: se comprueba el límite diario de mensajes (429), se guarda el mensaje del usuario y, si trata un
 * tema sensible ({@link FiltroTemasSensibles}), se responde con un texto seguro sin llamar al modelo (y sin contar
 * para el límite). Si no, se llama al asistente con el perfil resumido, las restricciones y el historial, fuera de
 * las transacciones, y se guarda la respuesta con las herramientas usadas y los tokens; el uso se registra (también
 * si el proveedor falla, 503).
 */
@Service
public class AsistenteServicio {

    static final int TAMANO_MAXIMO_PAGINA = 50;
    static final int LONGITUD_TITULO = 60;
    static final int LONGITUD_VISTA_PREVIA = 120;
    static final int MENSAJES_HISTORIAL = 12;
    static final String RESPUESTA_VACIA = "No he podido generar una respuesta. Inténtalo de nuevo en un momento.";

    private static final String NO_ENCONTRADA = "La conversación no existe";
    private static final Locale ES = Locale.forLanguageTag("es-ES");
    private static final Logger LOG = LoggerFactory.getLogger(AsistenteServicio.class);

    /** Lo preparado en la primera transacción: o el contexto para el modelo o ya la respuesta segura. */
    private record Preparacion(ContextoChat contexto, MensajeResponse respuestaSegura) {
    }

    private final ConversacionIaRepositorio conversacionRepositorio;
    private final MensajeIaRepositorio mensajeRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final PerfilDeportivoRepositorio perfilRepositorio;
    private final TipoActividadRepositorio tipoActividadRepositorio;
    private final RestriccionesServicio restriccionesServicio;
    private final EntrenamientoServicio entrenamientoServicio;
    private final LimiteIaServicio limiteIaServicio;
    private final UsoIaServicio usoIaServicio;
    private final ChatAsistentePuerto chatAsistente;
    private final FiltroTemasSensibles filtro;
    private final SeguridadUtils seguridadUtils;
    private final Clock reloj;
    private final TransactionTemplate escritura;

    public AsistenteServicio(ConversacionIaRepositorio conversacionRepositorio, MensajeIaRepositorio mensajeRepositorio,
            UsuarioRepositorio usuarioRepositorio, PerfilDeportivoRepositorio perfilRepositorio,
            TipoActividadRepositorio tipoActividadRepositorio, RestriccionesServicio restriccionesServicio,
            EntrenamientoServicio entrenamientoServicio, LimiteIaServicio limiteIaServicio,
            UsoIaServicio usoIaServicio, ChatAsistentePuerto chatAsistente, FiltroTemasSensibles filtro,
            SeguridadUtils seguridadUtils, Clock reloj, PlatformTransactionManager transacciones) {
        this.conversacionRepositorio = conversacionRepositorio;
        this.mensajeRepositorio = mensajeRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.perfilRepositorio = perfilRepositorio;
        this.tipoActividadRepositorio = tipoActividadRepositorio;
        this.restriccionesServicio = restriccionesServicio;
        this.entrenamientoServicio = entrenamientoServicio;
        this.limiteIaServicio = limiteIaServicio;
        this.usoIaServicio = usoIaServicio;
        this.chatAsistente = chatAsistente;
        this.filtro = filtro;
        this.seguridadUtils = seguridadUtils;
        this.reloj = reloj;
        this.escritura = new TransactionTemplate(transacciones);
    }

    // ---------------------------------------------------------------------------------------------
    // Conversaciones
    // ---------------------------------------------------------------------------------------------

    /** Conversación vacía, sin título hasta el primer mensaje. */
    @Transactional
    public ConversacionResponse crear() {
        ConversacionIa conversacion = new ConversacionIa();
        conversacion.setUsuario(usuarioRepositorio.getReferenceById(seguridadUtils.usuarioActualId()));
        return new ConversacionResponse(conversacionRepositorio.save(conversacion).getId(), null, false, List.of());
    }

    /**
     * Conversaciones del usuario, la del último mensaje más reciente primero; como mucho 50 por página.
     *
     * @param archivada solo las archivadas (true) o solo las activas (false); nulo, todas
     */
    @Transactional(readOnly = true)
    public PaginaResponse<ConversacionResumenResponse> listar(Boolean archivada, Pageable paginacion) {
        if (paginacion.getPageSize() > TAMANO_MAXIMO_PAGINA) {
            throw new PeticionInvalidaException("La petición contiene datos no válidos", List.of(new ErrorCampo(
                    "size", "El tamaño de página no puede superar " + TAMANO_MAXIMO_PAGINA)));
        }
        Pageable pagina = PageRequest.of(paginacion.getPageNumber(), paginacion.getPageSize());
        return PaginaResponse.de(conversacionRepositorio.buscar(seguridadUtils.usuarioActualId(), archivada, pagina),
                conversacion -> new ConversacionResumenResponse(conversacion.getId(), conversacion.getTitulo(),
                        conversacion.isArchivada(), conversacion.getUltimoMensajeEn(),
                        mensajeRepositorio.ultimo(conversacion.getId())
                                .map(mensaje -> recortar(mensaje.getContenido(), LONGITUD_VISTA_PREVIA))
                                .orElse(null)));
    }

    /** La conversación con todos sus mensajes en orden. */
    @Transactional(readOnly = true)
    public ConversacionResponse obtener(UUID id) {
        return respuesta(propia(id, seguridadUtils.usuarioActualId()));
    }

    /** Renombra o archiva; los campos nulos no cambian. */
    @Transactional
    public ConversacionResponse actualizar(UUID id, ActualizarConversacionRequest peticion) {
        ConversacionIa conversacion = propia(id, seguridadUtils.usuarioActualId());
        if (peticion.titulo() != null) {
            conversacion.setTitulo(peticion.titulo().strip());
        }
        if (peticion.archivada() != null) {
            conversacion.setArchivada(peticion.archivada());
        }
        return respuesta(conversacion);
    }

    /** Borra la conversación con sus mensajes. */
    @Transactional
    public void eliminar(UUID id) {
        conversacionRepositorio.delete(propia(id, seguridadUtils.usuarioActualId()));
    }

    // ---------------------------------------------------------------------------------------------
    // Mensajes
    // ---------------------------------------------------------------------------------------------

    /**
     * Envía un mensaje y devuelve la respuesta del asistente (o la respuesta segura).
     *
     * @throws RecursoNoEncontradoException 404 si la conversación no existe o es de otro usuario
     * @throws com.nutrisport.comun.excepcion.LimiteExcedidoException 429 si se ha alcanzado el límite diario
     * @throws com.nutrisport.comun.excepcion.ServicioNoDisponibleException 503 si el proveedor no responde (el
     *         mensaje del usuario queda guardado, sin respuesta)
     */
    public MensajeResponse enviarMensaje(UUID conversacionId, EnviarMensajeRequest peticion) {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        String contenido = peticion.contenido().strip();

        Preparacion preparacion = escritura.execute(estado -> {
            ConversacionIa conversacion = propia(conversacionId, usuarioId);
            limiteIaServicio.comprobar(usuarioId, FuncionIa.ASISTENTE);
            List<MensajeIa> historial = mensajeRepositorio.ultimos(conversacion.getId(), MENSAJES_HISTORIAL);
            mensajeRepositorio.save(new MensajeIa(conversacion, RolMensaje.USUARIO, contenido));
            if (conversacion.getTitulo() == null) {
                conversacion.setTitulo(titulo(contenido));
            }
            conversacion.setUltimoMensajeEn(ahora());

            Optional<RespuestaSegura> segura = filtro.evaluar(contenido);
            if (segura.isPresent()) {
                LOG.info("Mensaje al asistente con tema sensible ({}): respuesta segura sin llamar al modelo",
                        segura.get().motivo());
                return new Preparacion(null, mensaje(guardarRespuesta(conversacion, segura.get().texto(),
                        List.of(), null, null)));
            }
            return new Preparacion(new ContextoChat(usuarioId, perfilResumido(usuarioId),
                    restriccionesServicio.estrictasDe(usuarioId).descripcion(), historial, contenido), null);
        });
        if (preparacion.respuestaSegura() != null) {
            return preparacion.respuestaSegura();
        }

        long inicio = System.nanoTime();
        RespuestaChat respuesta;
        try {
            respuesta = chatAsistente.responder(preparacion.contexto());
        } catch (RuntimeException e) {
            registrarUso(usuarioId, null, inicio, false);
            throw e;
        }
        registrarUso(usuarioId, respuesta, inicio, true);

        return escritura.execute(estado -> {
            ConversacionIa conversacion = propia(conversacionId, usuarioId);
            String texto = respuesta.texto() == null || respuesta.texto().isBlank() ? RESPUESTA_VACIA
                    : respuesta.texto().strip();
            MensajeIa guardada = guardarRespuesta(conversacion, texto, respuesta.herramientasUsadas(),
                    respuesta.tokensEntrada(), respuesta.tokensSalida());
            conversacion.setUltimoMensajeEn(ahora());
            return mensaje(guardada);
        });
    }

    /**
     * Valora una respuesta del asistente con −1 o 1 (se puede cambiar).
     *
     * @throws RecursoNoEncontradoException 404 si el mensaje no existe o es de otro usuario
     * @throws ReglaNegocioException        422 si el mensaje no es del asistente
     */
    @Transactional
    public void valorar(UUID mensajeId, int valor) {
        MensajeIa mensaje = mensajeRepositorio.buscarDelUsuario(mensajeId, seguridadUtils.usuarioActualId())
                .orElseThrow(() -> new RecursoNoEncontradoException("El mensaje no existe"));
        if (mensaje.getRol() != RolMensaje.ASISTENTE) {
            throw new ReglaNegocioException("Solo se pueden valorar las respuestas del asistente");
        }
        mensaje.setValoracion((short) valor);
    }

    /**
     * 3 o 4 preguntas para empezar según la hora (zona de la aplicación) y si hay algún entrenamiento planificado o
     * realizado hoy.
     */
    @Transactional(readOnly = true)
    public List<String> sugerencias() {
        UUID usuarioId = seguridadUtils.usuarioActualId();
        LocalDate hoy = LocalDate.now(reloj);
        int hora = LocalTime.now(reloj).getHour();
        boolean entrenoHoy = entrenamientoServicio.listar(usuarioId, hoy, hoy, null).stream()
                .anyMatch(entrenamiento -> entrenamiento.estado() != EstadoEntrenamiento.OMITIDO);

        Set<String> sugerencias = new LinkedHashSet<>();
        if (entrenoHoy) {
            sugerencias.add(hora < 17 ? "¿Qué como antes de entrenar hoy?" : "¿Qué tomo después del entreno?");
        }
        if (hora < 12) {
            sugerencias.add("¿Cuánta proteína tomé ayer?");
            sugerencias.add("¿Qué desayuno me conviene hoy?");
        } else if (hora < 17) {
            sugerencias.add("¿Cómo voy de calorías hoy?");
            sugerencias.add("¿Qué ceno hoy según mi plan?");
        } else {
            sugerencias.add("¿Cuánta proteína he tomado hoy?");
            sugerencias.add("¿Qué ceno hoy según mi plan?");
        }
        sugerencias.add("Resumen de mi semana");
        sugerencias.add("Idea de cena alta en proteína");
        return new ArrayList<>(sugerencias).subList(0, Math.min(4, sugerencias.size()));
    }

    // ---------------------------------------------------------------------------------------------
    // Auxiliares
    // ---------------------------------------------------------------------------------------------

    private ConversacionIa propia(UUID id, UUID usuarioId) {
        return conversacionRepositorio.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADA));
    }

    private MensajeIa guardarRespuesta(ConversacionIa conversacion, String texto, List<String> herramientas,
            Integer tokensEntrada, Integer tokensSalida) {
        MensajeIa respuesta = new MensajeIa(conversacion, RolMensaje.ASISTENTE, texto);
        respuesta.setHerramientasUsadas(herramientas);
        respuesta.setTokensEntrada(tokensEntrada);
        respuesta.setTokensSalida(tokensSalida);
        return mensajeRepositorio.save(respuesta);
    }

    private void registrarUso(UUID usuarioId, RespuestaChat respuesta, long inicioNanos, boolean exito) {
        try {
            usoIaServicio.registrar(usuarioId, FuncionIa.ASISTENTE, respuesta == null ? null
                    : new ResultadoIa<>(respuesta.texto(), respuesta.tokensEntrada(), respuesta.tokensSalida(),
                            respuesta.modelo()), (System.nanoTime() - inicioNanos) / 1_000_000, exito);
        } catch (RuntimeException e) {
            LOG.warn("No se ha podido registrar el uso del asistente: {}", e.toString());
        }
    }

    /**
     * Perfil sin datos identificativos (§8.3): edad en un tramo de 5 años, objetivo, deporte, nivel de actividad y
     * días de entreno. Nunca nombre ni email.
     */
    private String perfilResumido(UUID usuarioId) {
        Optional<PerfilDeportivo> encontrado = perfilRepositorio.findByUsuarioId(usuarioId);
        if (encontrado.isEmpty()) {
            return "sin perfil deportivo";
        }
        PerfilDeportivo perfil = encontrado.get();
        List<String> partes = new ArrayList<>();
        if (perfil.getFechaNacimiento() != null) {
            int edad = Period.between(perfil.getFechaNacimiento(), LocalDate.now(reloj)).getYears();
            int desde = edad / 5 * 5;
            partes.add("edad aproximada " + desde + "-" + (desde + 4) + " años");
        }
        if (perfil.getObjetivo() != null) {
            partes.add("objetivo " + perfil.getObjetivo().name().toLowerCase(ES).replace('_', ' '));
        }
        if (perfil.getDeportePrincipal() != null) {
            partes.add("deporte " + tipoActividadRepositorio.findById(perfil.getDeportePrincipal())
                    .map(tipo -> tipo.getNombre())
                    .orElse(perfil.getDeportePrincipal().toLowerCase(ES).replace('_', ' ')));
        }
        if (perfil.getNivelActividad() != null) {
            partes.add("nivel de actividad " + perfil.getNivelActividad().name().toLowerCase(ES));
        }
        if (perfil.getDiasEntrenamientoSemana() != null) {
            partes.add("entrena " + perfil.getDiasEntrenamientoSemana() + " días por semana");
        }
        return partes.isEmpty() ? "perfil sin datos" : String.join(", ", partes);
    }

    /** Los primeros 60 caracteres del primer mensaje, en una línea. */
    static String titulo(String contenido) {
        String linea = contenido.replaceAll("\\s+", " ").strip();
        return linea.length() <= LONGITUD_TITULO ? linea : linea.substring(0, LONGITUD_TITULO).strip();
    }

    private static String recortar(String texto, int maximo) {
        String linea = texto.replaceAll("\\s+", " ").strip();
        return linea.length() <= maximo ? linea : linea.substring(0, maximo - 1).strip() + "…";
    }

    private ConversacionResponse respuesta(ConversacionIa conversacion) {
        return new ConversacionResponse(conversacion.getId(), conversacion.getTitulo(), conversacion.isArchivada(),
                mensajeRepositorio.deConversacion(conversacion.getId()).stream()
                        .map(AsistenteServicio::mensaje)
                        .toList());
    }

    private static MensajeResponse mensaje(MensajeIa mensaje) {
        return new MensajeResponse(mensaje.getId(), mensaje.getRol(), mensaje.getContenido(),
                mensaje.getHerramientasUsadas(), mensaje.getValoracion() == null ? null
                        : mensaje.getValoracion().intValue(), mensaje.getCreadoEn());
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS);
    }
}
