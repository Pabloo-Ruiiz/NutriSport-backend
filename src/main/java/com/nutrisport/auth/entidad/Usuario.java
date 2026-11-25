package com.nutrisport.auth.entidad;

import com.nutrisport.comun.entidad.EntidadBase;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Cuenta de usuario (tabla {@code usuario}) con sus roles (tabla {@code usuario_rol}). */
@Entity
@Table(name = "usuario")
public class Usuario extends EntidadBase {

    /** Normalizado en minúsculas antes de guardarlo (lo hace el servicio). */
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    /** Hash BCrypt; nunca sale de la capa de servicio. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoUsuario estado = EstadoUsuario.ACTIVO;

    @Column(name = "email_verificado_en")
    private OffsetDateTime emailVerificadoEn;

    @Column(name = "mayor_edad_declarada_en", nullable = false)
    private OffsetDateTime mayorEdadDeclaradaEn;

    @Column(name = "consentimiento_salud_en", nullable = false)
    private OffsetDateTime consentimientoSaludEn;

    @Column(name = "consentimiento_salud_version", nullable = false, length = 10)
    private String consentimientoSaludVersion;

    @Column(name = "consentimiento_salud_revocado_en")
    private OffsetDateTime consentimientoSaludRevocadoEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidades", nullable = false, length = 10)
    private Unidades unidades = Unidades.METRICO;

    @Column(name = "ultimo_acceso")
    private OffsetDateTime ultimoAcceso;

    @Column(name = "eliminado_en")
    private OffsetDateTime eliminadoEn;

    /** Si la tarea nocturna detecta patrones (insights) para este usuario (V17). */
    @Column(name = "insights_activos", nullable = false)
    private boolean insightsActivos = true;

    /** Se cargan siempre: el filtro JWT los necesita en cada petición. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_rol", joinColumns = @JoinColumn(name = "usuario_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Set<Rol> roles = new HashSet<>();

    public Usuario() {
    }

    // ---------------------------------------------------------------------------------------------
    // Métodos de dominio
    // ---------------------------------------------------------------------------------------------

    public boolean tieneRol(Rol rol) {
        return roles.contains(rol);
    }

    public boolean estaActivo() {
        return estado == EstadoUsuario.ACTIVO;
    }

    public boolean emailVerificado() {
        return emailVerificadoEn != null;
    }

    public boolean consentimientoVigente() {
        return consentimientoSaludRevocadoEn == null;
    }

    public void anadirRol(Rol rol) {
        roles.add(rol);
    }

    public void quitarRol(Rol rol) {
        roles.remove(rol);
    }

    // ---------------------------------------------------------------------------------------------
    // Getters y setters
    // ---------------------------------------------------------------------------------------------

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public EstadoUsuario getEstado() {
        return estado;
    }

    public void setEstado(EstadoUsuario estado) {
        this.estado = estado;
    }

    public OffsetDateTime getEmailVerificadoEn() {
        return emailVerificadoEn;
    }

    public void setEmailVerificadoEn(OffsetDateTime emailVerificadoEn) {
        this.emailVerificadoEn = emailVerificadoEn;
    }

    public OffsetDateTime getMayorEdadDeclaradaEn() {
        return mayorEdadDeclaradaEn;
    }

    public void setMayorEdadDeclaradaEn(OffsetDateTime mayorEdadDeclaradaEn) {
        this.mayorEdadDeclaradaEn = mayorEdadDeclaradaEn;
    }

    public OffsetDateTime getConsentimientoSaludEn() {
        return consentimientoSaludEn;
    }

    public void setConsentimientoSaludEn(OffsetDateTime consentimientoSaludEn) {
        this.consentimientoSaludEn = consentimientoSaludEn;
    }

    public String getConsentimientoSaludVersion() {
        return consentimientoSaludVersion;
    }

    public void setConsentimientoSaludVersion(String consentimientoSaludVersion) {
        this.consentimientoSaludVersion = consentimientoSaludVersion;
    }

    public OffsetDateTime getConsentimientoSaludRevocadoEn() {
        return consentimientoSaludRevocadoEn;
    }

    public void setConsentimientoSaludRevocadoEn(OffsetDateTime consentimientoSaludRevocadoEn) {
        this.consentimientoSaludRevocadoEn = consentimientoSaludRevocadoEn;
    }

    public Unidades getUnidades() {
        return unidades;
    }

    public void setUnidades(Unidades unidades) {
        this.unidades = unidades;
    }

    public OffsetDateTime getUltimoAcceso() {
        return ultimoAcceso;
    }

    public void setUltimoAcceso(OffsetDateTime ultimoAcceso) {
        this.ultimoAcceso = ultimoAcceso;
    }

    public OffsetDateTime getEliminadoEn() {
        return eliminadoEn;
    }

    public void setEliminadoEn(OffsetDateTime eliminadoEn) {
        this.eliminadoEn = eliminadoEn;
    }

    public boolean isInsightsActivos() {
        return insightsActivos;
    }

    public void setInsightsActivos(boolean insightsActivos) {
        this.insightsActivos = insightsActivos;
    }

    /** Vista de solo lectura; para cambiarlos usa {@link #anadirRol}, {@link #quitarRol} o {@link #setRoles}. */
    public Set<Rol> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    /** Sustituye los roles conservando la colección gestionada por Hibernate. */
    public void setRoles(Collection<Rol> roles) {
        this.roles.clear();
        if (roles != null) {
            this.roles.addAll(roles);
        }
    }
}
