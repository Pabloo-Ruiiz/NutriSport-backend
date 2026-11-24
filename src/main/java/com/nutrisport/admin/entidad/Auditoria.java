package com.nutrisport.admin.entidad;

import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.comun.entidad.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Acción administrativa registrada para trazabilidad. */
@Entity
@Table(name = "auditoria")
public class Auditoria extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Usuario administrador;

    @Column(name = "accion", nullable = false, length = 60)
    private String accion;

    @Column(name = "entidad", nullable = false, length = 40)
    private String entidad;

    @Column(name = "entidad_id", length = 60)
    private String entidadId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalle")
    private Map<String, Object> detalle;

    @Column(name = "ip", length = 45)
    private String ip;

    public Auditoria() {
    }

    public Usuario getAdministrador() {
        return administrador;
    }

    public void setAdministrador(Usuario administrador) {
        this.administrador = administrador;
    }

    public String getAccion() {
        return accion;
    }

    public void setAccion(String accion) {
        this.accion = accion;
    }

    public String getEntidad() {
        return entidad;
    }

    public void setEntidad(String entidad) {
        this.entidad = entidad;
    }

    public String getEntidadId() {
        return entidadId;
    }

    public void setEntidadId(String entidadId) {
        this.entidadId = entidadId;
    }

    public Map<String, Object> getDetalle() {
        return detalle == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(detalle));
    }

    public void setDetalle(Map<String, Object> detalle) {
        this.detalle = detalle == null ? null : new LinkedHashMap<>(detalle);
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}
