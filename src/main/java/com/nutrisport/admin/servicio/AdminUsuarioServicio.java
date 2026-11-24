package com.nutrisport.admin.servicio;

import com.nutrisport.admin.dto.ActualizarUsuarioAdminRequest;
import com.nutrisport.admin.dto.UsuarioAdminResponse;
import com.nutrisport.admin.mapper.AdminMapper;
import com.nutrisport.auth.entidad.EstadoUsuario;
import com.nutrisport.auth.entidad.Rol;
import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import com.nutrisport.auth.servicio.RefreshTokenServicio;
import com.nutrisport.comun.dto.ErrorCampo;
import com.nutrisport.comun.dto.PaginaResponse;
import com.nutrisport.comun.excepcion.PeticionInvalidaException;
import com.nutrisport.comun.excepcion.RecursoNoEncontradoException;
import com.nutrisport.comun.excepcion.ReglaNegocioException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de usuarios por un administrador (§2.2 y §3.I): búsqueda, bloqueo y roles. Solo datos de
 * cuenta, nunca de salud. Cada cambio queda auditado en la misma transacción.
 */
@Service
@Transactional
public class AdminUsuarioServicio {

    static final int TAMANO_MAXIMO_PAGINA = 100;

    private final UsuarioRepositorio usuarioRepositorio;
    private final RefreshTokenServicio refreshTokenServicio;
    private final AuditoriaServicio auditoriaServicio;
    private final SeguridadUtils seguridadUtils;
    private final AdminMapper adminMapper;

    public AdminUsuarioServicio(UsuarioRepositorio usuarioRepositorio, RefreshTokenServicio refreshTokenServicio,
            AuditoriaServicio auditoriaServicio, SeguridadUtils seguridadUtils, AdminMapper adminMapper) {
        this.usuarioRepositorio = usuarioRepositorio;
        this.refreshTokenServicio = refreshTokenServicio;
        this.auditoriaServicio = auditoriaServicio;
        this.seguridadUtils = seguridadUtils;
        this.adminMapper = adminMapper;
    }

    /** Usuarios cuyo nombre o email contiene {@code q}, opcionalmente de un estado; los más recientes primero. */
    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioAdminResponse> listar(String q, EstadoUsuario estado, Pageable paginacion) {
        String consulta = q == null || q.isBlank() ? "" : q.strip().toLowerCase(Locale.ROOT);
        Pageable pagina = PageRequest.of(paginacion.getPageNumber(),
                Math.min(paginacion.getPageSize(), TAMANO_MAXIMO_PAGINA), Sort.by(Sort.Direction.DESC, "creadoEn"));
        return PaginaResponse.de(usuarioRepositorio.buscarParaAdmin(consulta, estado, pagina), adminMapper::aRespuesta);
    }

    @Transactional(readOnly = true)
    public UsuarioAdminResponse obtener(UUID id) {
        return adminMapper.aRespuesta(usuario(id));
    }

    /**
     * Cambia el estado (ACTIVO o BLOQUEADO) y/o los roles. Un administrador no puede bloquearse ni quitarse
     * el rol ADMIN a sí mismo. Al bloquear se cierran todas las sesiones del usuario.
     */
    public UsuarioAdminResponse actualizar(UUID id, ActualizarUsuarioAdminRequest peticion) {
        Usuario usuario = usuario(id);
        boolean esElMismo = usuario.getId().equals(seguridadUtils.usuarioActualId());
        if (usuario.getEstado() == EstadoUsuario.ELIMINADO) {
            throw new ReglaNegocioException("No se puede modificar una cuenta eliminada");
        }
        Map<String, Object> detalle = new LinkedHashMap<>();

        if (peticion.estado() != null && peticion.estado() != usuario.getEstado()) {
            if (peticion.estado() == EstadoUsuario.ELIMINADO) {
                throw new PeticionInvalidaException("La petición contiene datos no válidos", List.of(
                        new ErrorCampo("estado", "Solo se puede cambiar a ACTIVO o BLOQUEADO")));
            }
            if (esElMismo && peticion.estado() == EstadoUsuario.BLOQUEADO) {
                throw new ReglaNegocioException("No puedes bloquear tu propia cuenta");
            }
            detalle.put("estadoAnterior", usuario.getEstado().name());
            detalle.put("estadoNuevo", peticion.estado().name());
            usuario.setEstado(peticion.estado());
            if (peticion.estado() == EstadoUsuario.BLOQUEADO) {
                refreshTokenServicio.revocarTodos(usuario.getId());
            }
        }

        if (peticion.roles() != null && !peticion.roles().equals(usuario.getRoles())) {
            if (esElMismo && !peticion.roles().contains(Rol.ADMIN)) {
                throw new ReglaNegocioException("No puedes quitarte el rol de administrador");
            }
            detalle.put("rolesAnteriores", nombres(usuario.getRoles()));
            detalle.put("rolesNuevos", nombres(peticion.roles()));
            EnumSet.allOf(Rol.class).forEach(rol -> {
                if (peticion.roles().contains(rol)) {
                    usuario.anadirRol(rol);
                } else {
                    usuario.quitarRol(rol);
                }
            });
        }

        if (!detalle.isEmpty()) {
            auditoriaServicio.registrar("ACTUALIZAR_USUARIO", "usuario", usuario.getId().toString(), detalle);
        }
        return adminMapper.aRespuesta(usuario);
    }

    private Usuario usuario(UUID id) {
        return usuarioRepositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe"));
    }

    private static List<String> nombres(Set<Rol> roles) {
        return List.copyOf(new TreeSet<>(roles.stream().map(Rol::name).toList()));
    }
}
