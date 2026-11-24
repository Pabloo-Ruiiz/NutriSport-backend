package com.nutrisport.admin.servicio;

import com.nutrisport.admin.entidad.Auditoria;
import com.nutrisport.admin.repositorio.AuditoriaRepositorio;
import com.nutrisport.auth.repositorio.UsuarioRepositorio;
import com.nutrisport.auth.seguridad.SeguridadUtils;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Registra acciones administrativas dentro de la transacción que realiza la acción. */
@Service
public class AuditoriaServicio {

    private final AuditoriaRepositorio auditoriaRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final SeguridadUtils seguridadUtils;

    public AuditoriaServicio(AuditoriaRepositorio auditoriaRepositorio, UsuarioRepositorio usuarioRepositorio,
            SeguridadUtils seguridadUtils) {
        this.auditoriaRepositorio = auditoriaRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.seguridadUtils = seguridadUtils;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void registrar(String accion, String entidad, String entidadId, Map<String, Object> detalle) {
        Auditoria auditoria = new Auditoria();
        auditoria.setAdministrador(usuarioRepositorio.getReferenceById(seguridadUtils.usuarioActualId()));
        auditoria.setAccion(accion);
        auditoria.setEntidad(entidad);
        auditoria.setEntidadId(entidadId);
        auditoria.setDetalle(detalle);
        auditoria.setIp(ipDeLaPeticion());
        auditoriaRepositorio.save(auditoria);
    }

    private static String ipDeLaPeticion() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            return atributos.getRequest().getRemoteAddr();
        }
        return null;
    }
}
