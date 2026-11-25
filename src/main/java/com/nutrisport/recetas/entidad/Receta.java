package com.nutrisport.recetas.entidad;

import com.nutrisport.auth.entidad.Usuario;
import com.nutrisport.catalogo.entidad.Categoria;
import com.nutrisport.catalogo.entidad.Visibilidad;
import com.nutrisport.comun.entidad.EntidadBase;
import com.nutrisport.comun.util.TextoNormalizado;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Receta propia o importada de TheMealDB (tabla {@code receta}, §6.1, Ampliación). Las importadas están en
 * inglés, con raciones supuestas (4) y sin nutrientes ni alérgenos: estos se calculan y se derivan de los
 * ingredientes emparejados con alimentos del catálogo. Solo las publicadas entran en el planificador.
 */
@Entity
@Table(name = "receta")
public class Receta extends EntidadBase {

    public static final short RACIONES_POR_DEFECTO = 4;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, length = 10)
    private OrigenReceta origen;

    /** idMeal de TheMealDB; nulo en las propias (CHECK ck_receta_referencia). */
    @Column(name = "referencia_externa", length = 20)
    private String referenciaExterna;

    @Column(name = "nombre", nullable = false, length = 150)
    private String nombre;

    /** Lo rellena {@link #normalizarNombre()} antes de insertar o actualizar. */
    @Column(name = "nombre_normalizado", nullable = false, length = 150)
    private String nombreNormalizado;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    /** "es" en las propias, "en" en las de TheMealDB. */
    @Column(name = "idioma", nullable = false, length = 5)
    private String idioma = "es";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    /** strCategory original de TheMealDB. */
    @Column(name = "categoria_externa", length = 40)
    private String categoriaExterna;

    /** strArea original de TheMealDB. */
    @Column(name = "area", length = 40)
    private String area;

    /** strMealThumb: se enlaza con atribución, no se copia. */
    @Column(name = "imagen_url", length = 300)
    private String imagenUrl;

    @Column(name = "raciones", nullable = false)
    private short raciones = RACIONES_POR_DEFECTO;

    /** {@code true} mientras las raciones no las fije el administrador. */
    @Column(name = "raciones_supuestas", nullable = false)
    private boolean racionesSupuestas = true;

    @Column(name = "tiempo_preparacion_min")
    private Short tiempoPreparacionMin;

    @Enumerated(EnumType.STRING)
    @Column(name = "dificultad", length = 10)
    private Dificultad dificultad;

    /** Constantes de TipoComida en las que encaja la receta (CHECK ck_receta_franjas). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "franjas_aptas", nullable = false)
    private List<String> franjasAptas = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pasos", nullable = false)
    private List<PasoReceta> pasos = new ArrayList<>();

    /** "alta-proteina", "pre-entreno"... */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "etiquetas", nullable = false)
    private List<String> etiquetas = new ArrayList<>();

    @Column(name = "kcal_racion", precision = 7, scale = 1)
    private BigDecimal kcalRacion;

    @Column(name = "proteina_racion", precision = 7, scale = 1)
    private BigDecimal proteinaRacion;

    @Column(name = "carbohidratos_racion", precision = 7, scale = 1)
    private BigDecimal carbohidratosRacion;

    @Column(name = "grasa_racion", precision = 7, scale = 1)
    private BigDecimal grasaRacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nutricional", nullable = false, length = 12)
    private EstadoNutricional estadoNutricional = EstadoNutricional.INCOMPLETO;

    /** % de la masa de los ingredientes con datos nutricionales (0–100). */
    @Column(name = "completitud_pct", nullable = false)
    private short completitudPct;

    /** Constantes de Alergeno, derivadas de los ingredientes. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "alergenos_contiene", nullable = false)
    private List<String> alergenosContiene = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "alergenos_trazas", nullable = false)
    private List<String> alergenosTrazas = new ArrayList<>();

    @Column(name = "contiene_lactosa", nullable = false)
    private boolean contieneLactosa;

    @Column(name = "apto_vegano", nullable = false)
    private boolean aptoVegano;

    @Column(name = "apto_vegetariano", nullable = false)
    private boolean aptoVegetariano;

    @Column(name = "apto_pescetariano", nullable = false)
    private boolean aptoPescetariano;

    /** {@code true} solo si todos los ingredientes están emparejados con alimentos con alérgenos revisados. */
    @Column(name = "alergenos_verificados", nullable = false)
    private boolean alergenosVerificados;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibilidad", nullable = false, length = 10)
    private Visibilidad visibilidad;

    /** Obligatorio si es privada (CHECK ck_receta_privada_con_propietario). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "propietario_id")
    private Usuario propietario;

    @Column(name = "publicada", nullable = false)
    private boolean publicada;

    @Column(name = "sincronizado_en")
    private OffsetDateTime sincronizadoEn;

    @OneToMany(mappedBy = "receta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<RecetaIngrediente> ingredientes = new ArrayList<>();

    public Receta() {
    }

    // ---------------------------------------------------------------------------------------------
    // Métodos de dominio
    // ---------------------------------------------------------------------------------------------

    /** Si el usuario puede verla: las públicas cualquiera y las privadas solo su propietario. */
    public boolean esVisiblePara(UUID usuarioId) {
        if (visibilidad == Visibilidad.PUBLICO) {
            return true;
        }
        // getId() de un proxy perezoso no lo inicializa
        return propietario != null && usuarioId != null && usuarioId.equals(propietario.getId());
    }

    /** Añade un ingrediente al final y fija su orden. */
    public void anadirIngrediente(RecetaIngrediente ingrediente) {
        ingrediente.setReceta(this);
        ingrediente.setOrden((short) (ingredientes.size() + 1));
        ingredientes.add(ingrediente);
    }

    /** Quita todos los ingredientes (se borran al guardar: orphanRemoval). */
    public void vaciarIngredientes() {
        ingredientes.forEach(ingrediente -> ingrediente.setReceta(null));
        ingredientes.clear();
    }

    @PrePersist
    @PreUpdate
    protected void normalizarNombre() {
        this.nombreNormalizado = TextoNormalizado.normalizar(nombre);
    }

    // ---------------------------------------------------------------------------------------------
    // Getters y setters
    // ---------------------------------------------------------------------------------------------

    public OrigenReceta getOrigen() {
        return origen;
    }

    public void setOrigen(OrigenReceta origen) {
        this.origen = origen;
    }

    public String getReferenciaExterna() {
        return referenciaExterna;
    }

    public void setReferenciaExterna(String referenciaExterna) {
        this.referenciaExterna = referenciaExterna;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getNombreNormalizado() {
        return nombreNormalizado;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getIdioma() {
        return idioma;
    }

    public void setIdioma(String idioma) {
        this.idioma = idioma;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public void setCategoria(Categoria categoria) {
        this.categoria = categoria;
    }

    public String getCategoriaExterna() {
        return categoriaExterna;
    }

    public void setCategoriaExterna(String categoriaExterna) {
        this.categoriaExterna = categoriaExterna;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }

    public void setImagenUrl(String imagenUrl) {
        this.imagenUrl = imagenUrl;
    }

    public short getRaciones() {
        return raciones;
    }

    public void setRaciones(short raciones) {
        this.raciones = raciones;
    }

    public boolean isRacionesSupuestas() {
        return racionesSupuestas;
    }

    public void setRacionesSupuestas(boolean racionesSupuestas) {
        this.racionesSupuestas = racionesSupuestas;
    }

    public Short getTiempoPreparacionMin() {
        return tiempoPreparacionMin;
    }

    public void setTiempoPreparacionMin(Short tiempoPreparacionMin) {
        this.tiempoPreparacionMin = tiempoPreparacionMin;
    }

    public Dificultad getDificultad() {
        return dificultad;
    }

    public void setDificultad(Dificultad dificultad) {
        this.dificultad = dificultad;
    }

    public List<String> getFranjasAptas() {
        return franjasAptas;
    }

    public void setFranjasAptas(List<String> franjasAptas) {
        this.franjasAptas = franjasAptas == null ? new ArrayList<>() : new ArrayList<>(franjasAptas);
    }

    public List<PasoReceta> getPasos() {
        return pasos;
    }

    public void setPasos(List<PasoReceta> pasos) {
        this.pasos = pasos == null ? new ArrayList<>() : new ArrayList<>(pasos);
    }

    public List<String> getEtiquetas() {
        return etiquetas;
    }

    public void setEtiquetas(List<String> etiquetas) {
        this.etiquetas = etiquetas == null ? new ArrayList<>() : new ArrayList<>(etiquetas);
    }

    public BigDecimal getKcalRacion() {
        return kcalRacion;
    }

    public void setKcalRacion(BigDecimal kcalRacion) {
        this.kcalRacion = kcalRacion;
    }

    public BigDecimal getProteinaRacion() {
        return proteinaRacion;
    }

    public void setProteinaRacion(BigDecimal proteinaRacion) {
        this.proteinaRacion = proteinaRacion;
    }

    public BigDecimal getCarbohidratosRacion() {
        return carbohidratosRacion;
    }

    public void setCarbohidratosRacion(BigDecimal carbohidratosRacion) {
        this.carbohidratosRacion = carbohidratosRacion;
    }

    public BigDecimal getGrasaRacion() {
        return grasaRacion;
    }

    public void setGrasaRacion(BigDecimal grasaRacion) {
        this.grasaRacion = grasaRacion;
    }

    public EstadoNutricional getEstadoNutricional() {
        return estadoNutricional;
    }

    public void setEstadoNutricional(EstadoNutricional estadoNutricional) {
        this.estadoNutricional = estadoNutricional;
    }

    public short getCompletitudPct() {
        return completitudPct;
    }

    public void setCompletitudPct(short completitudPct) {
        this.completitudPct = completitudPct;
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

    public boolean isAlergenosVerificados() {
        return alergenosVerificados;
    }

    public void setAlergenosVerificados(boolean alergenosVerificados) {
        this.alergenosVerificados = alergenosVerificados;
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

    public boolean isPublicada() {
        return publicada;
    }

    public void setPublicada(boolean publicada) {
        this.publicada = publicada;
    }

    public OffsetDateTime getSincronizadoEn() {
        return sincronizadoEn;
    }

    public void setSincronizadoEn(OffsetDateTime sincronizadoEn) {
        this.sincronizadoEn = sincronizadoEn;
    }

    /** Ingredientes en su orden; para cambiarlos, {@link #anadirIngrediente} y {@link #vaciarIngredientes}. */
    public List<RecetaIngrediente> getIngredientes() {
        return Collections.unmodifiableList(ingredientes);
    }
}
