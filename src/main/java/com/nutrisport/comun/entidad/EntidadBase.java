package com.nutrisport.comun.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.Hibernate;
import org.hibernate.annotations.UuidGenerator;

/**
 * Superclase de las entidades: identificador UUID ordenable por tiempo generado por la aplicación
 * (§5.6) y las columnas de auditoría {@code creado_en} y {@code actualizado_en} (timestamptz, §6.1).
 */
@MappedSuperclass
public abstract class EntidadBase {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn;

    @PrePersist
    protected void alCrear() {
        OffsetDateTime ahora = ahora();
        this.creadoEn = ahora;
        this.actualizadoEn = ahora;
    }

    @PreUpdate
    protected void alActualizar() {
        this.actualizadoEn = ahora();
    }

    /** Instante actual en UTC truncado a microsegundos, la precisión de timestamptz en PostgreSQL. */
    private static OffsetDateTime ahora() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public OffsetDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    /**
     * Dos entidades son iguales si son de la misma clase y tienen el mismo id. Una entidad sin
     * persistir (id nulo) solo es igual a sí misma. Se usa {@link Hibernate#getClass} para que
     * un proxy perezoso sea igual a la entidad real.
     */
    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (otro == null || Hibernate.getClass(this) != Hibernate.getClass(otro)) {
            return false;
        }
        EntidadBase entidad = (EntidadBase) otro;
        return id != null && id.equals(entidad.getId());
    }

    /**
     * Constante por clase: el id se asigna al persistir y, si el hash dependiera de él, una entidad
     * guardada en un HashSet antes de persistirse dejaría de encontrarse después.
     */
    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
