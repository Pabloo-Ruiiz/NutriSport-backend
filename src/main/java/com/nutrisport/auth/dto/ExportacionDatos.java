package com.nutrisport.auth.dto;

import com.nutrisport.actividad.dto.EntrenamientoResponse;
import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.entidad.Unidades;
import com.nutrisport.comidas.dto.CandidatoAnalisis;
import com.nutrisport.comidas.dto.RegistroComidaResponse;
import com.nutrisport.comidas.entidad.EstadoAnalisis;
import com.nutrisport.comidas.entidad.TipoAnalisis;
import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.compra.entidad.EntradaDesglose;
import com.nutrisport.compra.entidad.EstadoLista;
import com.nutrisport.compra.entidad.UnidadCompra;
import com.nutrisport.asistente.entidad.RolMensaje;
import com.nutrisport.menus.entidad.EstadoMenu;
import com.nutrisport.menus.entidad.TipoMenu;
import com.nutrisport.perfil.dto.ObjetivoDiarioResponse;
import com.nutrisport.perfil.dto.PerfilResponse;
import com.nutrisport.perfil.dto.PreferenciaDto;
import com.nutrisport.seguimiento.dto.InsightResponse;
import com.nutrisport.seguimiento.dto.PesoResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Todos los datos del usuario (GET /api/v1/cuenta/exportacion, §8.3 y §7.1: derecho de acceso y portabilidad). Nunca
 * incluye el hash de la contraseña, los tokens ni las fotografías (de los análisis solo van los metadatos y los
 * candidatos).
 *
 * @param perfil nulo si el usuario no tiene perfil
 */
public record ExportacionDatos(
        OffsetDateTime fechaExportacion,
        Cuenta cuenta,
        PerfilResponse perfil,
        List<PreferenciaDto> preferencias,
        List<ObjetivoDiarioResponse> objetivosDiarios,
        List<RegistroComidaResponse> comidas,
        List<EntrenamientoResponse> entrenamientos,
        List<PesoResponse> pesos,
        List<Menu> menus,
        List<ListaCompra> listasCompra,
        List<RecetaFavorita> recetasFavoritas,
        List<Conversacion> conversaciones,
        List<Analisis> analisis,
        List<InsightResponse> insights) {

    /** Datos de la cuenta y de sus consentimientos, sin contraseña ni tokens. */
    public record Cuenta(UUID id, String nombre, String email, Set<Rol> roles, EstadoUsuario estado,
            OffsetDateTime emailVerificadoEn, OffsetDateTime mayorEdadDeclaradaEn,
            OffsetDateTime consentimientoSaludEn, String consentimientoSaludVersion,
            OffsetDateTime consentimientoSaludRevocadoEn, Unidades unidades, boolean insightsActivos,
            OffsetDateTime creadoEn, OffsetDateTime ultimoAcceso) {
    }

    public record Menu(UUID id, String nombre, TipoMenu tipo, LocalDate fechaInicio, LocalDate fechaFin,
            boolean favorito, EstadoMenu estado, boolean generadoAutomaticamente, String explicacion,
            OffsetDateTime creadoEn, List<ComidaMenu> comidas) {
    }

    /** @param registroComidaId la comida registrada al consumirla; nulo si no */
    public record ComidaMenu(LocalDate fecha, TipoComida tipoComida, UUID recetaId, String receta,
            BigDecimal raciones, boolean bloqueada, boolean consumida, UUID registroComidaId) {
    }

    public record ListaCompra(UUID id, String nombre, EstadoLista estado, UUID menuId, LocalDate fechaDesde,
            LocalDate fechaHasta, OffsetDateTime creadoEn, List<ProductoCompra> productos) {
    }

    public record ProductoCompra(String nombre, UUID alimentoId, BigDecimal cantidad, UnidadCompra unidad,
            String seccion, boolean comprado, boolean manual, List<EntradaDesglose> desglose) {
    }

    public record RecetaFavorita(UUID recetaId, String nombre, OffsetDateTime guardadaEn) {
    }

    public record Conversacion(UUID id, String titulo, boolean archivada, OffsetDateTime ultimoMensajeEn,
            OffsetDateTime creadoEn, List<Mensaje> mensajes) {
    }

    public record Mensaje(RolMensaje rol, String contenido, List<String> herramientasUsadas, Integer tokensEntrada,
            Integer tokensSalida, Integer valoracion, OffsetDateTime creadoEn) {
    }

    /** Un análisis de foto o etiqueta: metadatos y candidatos, sin la imagen. */
    public record Analisis(UUID id, TipoAnalisis tipo, EstadoAnalisis estado, TipoComida tipoComida,
            String descripcionUsuario, String proveedor, String modelo, List<CandidatoAnalisis> candidatos,
            UUID registroComidaId, OffsetDateTime creadoEn) {
    }
}
