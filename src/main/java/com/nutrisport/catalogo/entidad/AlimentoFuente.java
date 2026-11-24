package com.nutrisport.catalogo.entidad;

import com.nutrisport.comun.entidad.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Procedencia de un alimento en un proveedor externo (tabla {@code alimento_fuente}, §6.1, Ampliación).
 * Un alimento puede tener varias, y cada pareja (proveedor, referencia externa) es única.
 * <p>
 * El proveedor es un {@link FuenteDato} porque la columna admite {@code OPEN_FOOD_FACTS}, {@code USDA} y
 * {@code BEDCA} (CHECK {@code ck_alimento_fuente_proveedor}): BEDCA es una fuente de datos que se importa
 * por CSV, no una API, así que no está en {@code ProveedorExterno}.
 */
@Entity
@Table(name = "alimento_fuente")
public class AlimentoFuente extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alimento_id", nullable = false)
    private Alimento alimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "proveedor", nullable = false, length = 20)
    private FuenteDato proveedor;

    /** Código de barras (Open Food Facts), fdcId (USDA) o código BEDCA. */
    @Column(name = "referencia_externa", nullable = false, length = 40)
    private String referenciaExterna;

    /** Ficha del producto en el proveedor, para la atribución. */
    @Column(name = "url", length = 300)
    private String url;

    /** ODbL, CC0... */
    @Column(name = "licencia", length = 20)
    private String licencia;

    /** Última vez que se trajeron los datos del proveedor (se refrescan pasados 30 días). */
    @Column(name = "sincronizado_en", nullable = false)
    private OffsetDateTime sincronizadoEn;

    public AlimentoFuente() {
    }

    public Alimento getAlimento() {
        return alimento;
    }

    public void setAlimento(Alimento alimento) {
        this.alimento = alimento;
    }

    public FuenteDato getProveedor() {
        return proveedor;
    }

    public void setProveedor(FuenteDato proveedor) {
        this.proveedor = proveedor;
    }

    public String getReferenciaExterna() {
        return referenciaExterna;
    }

    public void setReferenciaExterna(String referenciaExterna) {
        this.referenciaExterna = referenciaExterna;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLicencia() {
        return licencia;
    }

    public void setLicencia(String licencia) {
        this.licencia = licencia;
    }

    public OffsetDateTime getSincronizadoEn() {
        return sincronizadoEn;
    }

    public void setSincronizadoEn(OffsetDateTime sincronizadoEn) {
        this.sincronizadoEn = sincronizadoEn;
    }
}
