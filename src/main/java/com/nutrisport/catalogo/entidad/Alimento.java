package com.nutrisport.catalogo.entidad;

import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.comun.entidad.EntidadBase;
import com.nutrisport.comun.util.TextoNormalizado;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Alimento del catálogo (tabla {@code alimento}, §6.1) con sus valores por 100 g o 100 ml y la fuente
 * de los datos (trazabilidad, §8.6). Los nutrientes secundarios nulos significan "dato no disponible",
 * nunca 0. Un alimento ya usado en registros no se borra: se desactiva ({@code activo = false}).
 */
@Entity
@Table(name = "alimento")
public class Alimento extends EntidadBase {

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    /** Lo rellena {@link #normalizarNombre()} antes de insertar o actualizar. */
    @Column(name = "nombre_normalizado", nullable = false, length = 150)
    private String nombreNormalizado;

    @Column(name = "marca", length = 80)
    private String marca;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(name = "codigo_barras", length = 14)
    private String codigoBarras;

    @Enumerated(EnumType.STRING)
    @Column(name = "preparacion", nullable = false, length = 10)
    private Preparacion preparacion = Preparacion.OTRA;

    @Column(name = "kcal100", nullable = false, precision = 7, scale = 2)
    private BigDecimal kcal100;

    @Column(name = "proteinas100", nullable = false, precision = 7, scale = 2)
    private BigDecimal proteinas100;

    @Column(name = "carbohidratos100", nullable = false, precision = 7, scale = 2)
    private BigDecimal carbohidratos100;

    @Column(name = "grasas100", nullable = false, precision = 7, scale = 2)
    private BigDecimal grasas100;

    @Column(name = "azucares100", precision = 7, scale = 2)
    private BigDecimal azucares100;

    @Column(name = "saturadas100", precision = 7, scale = 2)
    private BigDecimal saturadas100;

    @Column(name = "fibra100", precision = 7, scale = 2)
    private BigDecimal fibra100;

    @Column(name = "sal100", precision = 7, scale = 2)
    private BigDecimal sal100;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_base", nullable = false, length = 2)
    private UnidadBase unidadBase;

    /** Medidas caseras (columna jsonb). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "porciones", nullable = false)
    private List<PorcionCasera> porciones = new ArrayList<>();

    /** Alérgenos que contiene: constantes del enum Alergeno (CHECK ck_alimento_alergenos_contiene). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "alergenos_contiene", nullable = false)
    private List<String> alergenosContiene = new ArrayList<>();

    /** Alérgenos que puede contener en trazas; en las alergias estrictas también excluyen. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "alergenos_trazas", nullable = false)
    private List<String> alergenosTrazas = new ArrayList<>();

    @Column(name = "alergenos_revisados", nullable = false)
    private boolean alergenosRevisados;

    @Column(name = "contiene_lactosa", nullable = false)
    private boolean contieneLactosa;

    @Column(name = "apto_vegano", nullable = false)
    private boolean aptoVegano;

    @Column(name = "apto_vegetariano", nullable = false)
    private boolean aptoVegetariano;

    @Column(name = "apto_pescetariano", nullable = false)
    private boolean aptoPescetariano;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuente", nullable = false, length = 20)
    private FuenteDato fuente;

    @Column(name = "referencia_externa", length = 80)
    private String referenciaExterna;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibilidad", nullable = false, length = 10)
    private Visibilidad visibilidad;

    /** Obligatorio si es privado (CHECK ck_alimento_privado_con_propietario). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "propietario_id")
    private Usuario propietario;

    @Column(name = "validado", nullable = false)
    private boolean validado;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    public Alimento() {
    }

    // ---------------------------------------------------------------------------------------------
    // Métodos de dominio
    // ---------------------------------------------------------------------------------------------

    /**
     * Si el usuario puede ver el alimento: los públicos los ve cualquiera y los privados solo su
     * propietario. No tiene en cuenta {@code activo}: un alimento desactivado sigue siendo visible en
     * el historial de quien lo usó.
     */
    public boolean esVisiblePara(UUID usuarioId) {
        if (visibilidad == Visibilidad.PUBLICO) {
            return true;
        }
        // getId() de un proxy perezoso no lo inicializa
        return propietario != null && usuarioId != null && usuarioId.equals(propietario.getId());
    }

    /** Si es un alimento privado de ese usuario: solo entonces puede editarlo o borrarlo. */
    public boolean esPrivadoDe(UUID usuarioId) {
        return visibilidad == Visibilidad.PRIVADO && propietario != null && usuarioId != null
                && usuarioId.equals(propietario.getId());
    }

    @PrePersist
    @PreUpdate
    protected void normalizarNombre() {
        this.nombreNormalizado = TextoNormalizado.normalizar(nombre);
    }

    // ---------------------------------------------------------------------------------------------
    // Getters y setters
    // ---------------------------------------------------------------------------------------------

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getNombreNormalizado() {
        return nombreNormalizado;
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public void setCategoria(Categoria categoria) {
        this.categoria = categoria;
    }

    public String getCodigoBarras() {
        return codigoBarras;
    }

    public void setCodigoBarras(String codigoBarras) {
        this.codigoBarras = codigoBarras;
    }

    public Preparacion getPreparacion() {
        return preparacion;
    }

    public void setPreparacion(Preparacion preparacion) {
        this.preparacion = preparacion;
    }

    public BigDecimal getKcal100() {
        return kcal100;
    }

    public void setKcal100(BigDecimal kcal100) {
        this.kcal100 = kcal100;
    }

    public BigDecimal getProteinas100() {
        return proteinas100;
    }

    public void setProteinas100(BigDecimal proteinas100) {
        this.proteinas100 = proteinas100;
    }

    public BigDecimal getCarbohidratos100() {
        return carbohidratos100;
    }

    public void setCarbohidratos100(BigDecimal carbohidratos100) {
        this.carbohidratos100 = carbohidratos100;
    }

    public BigDecimal getGrasas100() {
        return grasas100;
    }

    public void setGrasas100(BigDecimal grasas100) {
        this.grasas100 = grasas100;
    }

    public BigDecimal getAzucares100() {
        return azucares100;
    }

    public void setAzucares100(BigDecimal azucares100) {
        this.azucares100 = azucares100;
    }

    public BigDecimal getSaturadas100() {
        return saturadas100;
    }

    public void setSaturadas100(BigDecimal saturadas100) {
        this.saturadas100 = saturadas100;
    }

    public BigDecimal getFibra100() {
        return fibra100;
    }

    public void setFibra100(BigDecimal fibra100) {
        this.fibra100 = fibra100;
    }

    public BigDecimal getSal100() {
        return sal100;
    }

    public void setSal100(BigDecimal sal100) {
        this.sal100 = sal100;
    }

    public UnidadBase getUnidadBase() {
        return unidadBase;
    }

    public void setUnidadBase(UnidadBase unidadBase) {
        this.unidadBase = unidadBase;
    }

    public List<PorcionCasera> getPorciones() {
        return porciones;
    }

    public void setPorciones(List<PorcionCasera> porciones) {
        this.porciones = porciones == null ? new ArrayList<>() : new ArrayList<>(porciones);
    }

    public List<String> getAlergenosContiene() {
        return alergenosContiene;
    }

    public void setAlergenosContiene(List<String> alergenosContiene) {
        this.alergenosContiene = alergenosContiene == null ? new ArrayList<>() : new ArrayList<>(alergenosContiene);
    }

    public List<String> getAlergenosTrazas() {
        return alergenosTrazas;
    }

    public void setAlergenosTrazas(List<String> alergenosTrazas) {
        this.alergenosTrazas = alergenosTrazas == null ? new ArrayList<>() : new ArrayList<>(alergenosTrazas);
    }

    public boolean isAlergenosRevisados() {
        return alergenosRevisados;
    }

    public void setAlergenosRevisados(boolean alergenosRevisados) {
        this.alergenosRevisados = alergenosRevisados;
    }

    public boolean isContieneLactosa() {
        return contieneLactosa;
    }

    public void setContieneLactosa(boolean contieneLactosa) {
        this.contieneLactosa = contieneLactosa;
    }

    public boolean isAptoVegano() {
        return aptoVegano;
    }

    public void setAptoVegano(boolean aptoVegano) {
        this.aptoVegano = aptoVegano;
    }

    public boolean isAptoVegetariano() {
        return aptoVegetariano;
    }

    public void setAptoVegetariano(boolean aptoVegetariano) {
        this.aptoVegetariano = aptoVegetariano;
    }

    public boolean isAptoPescetariano() {
        return aptoPescetariano;
    }

    public void setAptoPescetariano(boolean aptoPescetariano) {
        this.aptoPescetariano = aptoPescetariano;
    }

    public FuenteDato getFuente() {
        return fuente;
    }

    public void setFuente(FuenteDato fuente) {
        this.fuente = fuente;
    }

    public String getReferenciaExterna() {
        return referenciaExterna;
    }

    public void setReferenciaExterna(String referenciaExterna) {
        this.referenciaExterna = referenciaExterna;
    }

    public Visibilidad getVisibilidad() {
        return visibilidad;
    }

    public void setVisibilidad(Visibilidad visibilidad) {
        this.visibilidad = visibilidad;
    }

    public Usuario getPropietario() {
        return propietario;
    }

    public void setPropietario(Usuario propietario) {
        this.propietario = propietario;
    }

    public boolean isValidado() {
        return validado;
    }

    public void setValidado(boolean validado) {
        this.validado = validado;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
