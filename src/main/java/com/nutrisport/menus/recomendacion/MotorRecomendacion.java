package com.nutrisport.menus.recomendacion;

import com.nutrisport.comidas.entidad.TipoComida;
import com.nutrisport.comun.dto.Nutrientes;
import com.nutrisport.perfil.entidad.Dieta;
import com.nutrisport.perfil.servicio.RestriccionesUsuario;
import com.nutrisport.recetas.entidad.EstadoNutricional;
import com.nutrisport.recetas.entidad.Receta;
import com.nutrisport.recetas.servicio.CompatibilidadReceta;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Motor de recomendación de menús (§4.5): heurística determinista, sin Spring ni base de datos y sin aleatoriedad
 * (los empates se deshacen por nombre e id de la receta). Reglas:
 * <table>
 * <caption>Reglas del motor</caption>
 * <tr><td>Franjas por día</td><td>{@link RepartoFranjas}</td></tr>
 * <tr><td>Filtro duro</td><td>{@link #esCandidata}: publicada, cálculo COMPLETO o APROXIMADO, apta para la franja,
 * compatible con las restricciones estrictas (con alergias, además alérgenos verificados) y con un tiempo de
 * preparación que no supere el máximo del perfil. Que sea pública o propia del usuario lo garantiza la consulta que
 * carga las candidatas.</td></tr>
 * <tr><td>Puntuación</td><td>{@link PuntuadorRecetas#puntuar} (menor es mejor)</td></tr>
 * <tr><td>Raciones</td><td>0,75 · 1 · 1,25 · 1,5 · 2, la que mejor ajuste ({@link PuntuadorRecetas#mejoresRaciones})</td></tr>
 * <tr><td>Construcción del día</td><td>voraz por franjas con las 5 mejores candidatas de cada una y
 * <i>backtracking</i> limitado a 300 nodos hasta que el día quede en ±10 % de kcal y ±15 % de proteína; si no se
 * logra, la mejor combinación encontrada y un aviso.</td></tr>
 * <tr><td>Bloqueadas</td><td>se respetan y su energía se descuenta del día: las franjas libres se reparten lo que
 * queda.</td></tr>
 * </table>
 * El objetivo de cada franja libre se recalcula en cada paso de la búsqueda: lo que falta del día (objetivo − lo
 * bloqueado − lo ya elegido) en proporción a su porcentaje entre las franjas que quedan. Así una franja que se pasa
 * la compensan las siguientes. Una receta no se repite en el mismo día salvo que no quede otra apta para la franja.
 */
public final class MotorRecomendacion {

    public static final int MAXIMO_NODOS = 300;
    public static final int CANDIDATAS_POR_FRANJA = 5;
    public static final BigDecimal TOLERANCIA_KCAL_PCT = new BigDecimal("10");
    public static final BigDecimal TOLERANCIA_PROTEINA_PCT = new BigDecimal("15");

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final Locale ES = Locale.forLanguageTag("es-ES");

    /** Orden estable para los empates: nombre y después id. */
    private static final Comparator<Receta> ORDEN_ESTABLE = Comparator
            .comparing((Receta receta) -> receta.getNombre() == null ? "" : receta.getNombre())
            .thenComparing(receta -> receta.getId() == null ? "" : receta.getId().toString());

    /**
     * Día generado.
     *
     * @param selecciones las recetas de cada franja en orden del día, bloqueadas incluidas
     * @param total       nutrientes del día (bloqueadas incluidas), con un decimal
     * @param avisos      franjas sin recetas aptas o día fuera de la tolerancia; vacío si todo cuadra
     */
    public record ResultadoDia(List<SeleccionFranja> selecciones, Nutrientes total, List<String> avisos) {

        public ResultadoDia {
            selecciones = List.copyOf(selecciones);
            avisos = List.copyOf(avisos);
        }
    }

    /** Una franja libre con sus recetas aptas, ya en orden estable. */
    private record Franja(TipoComida tipo, BigDecimal porcentaje, List<Receta> aptas) {
    }

    /** Una receta con sus mejores raciones y su puntuación para una franja. */
    private record Opcion(Receta receta, BigDecimal raciones, Nutrientes aporte, double puntuacion) {
    }

    // ---------------------------------------------------------------------------------------------
    // Filtro duro
    // ---------------------------------------------------------------------------------------------

    /** Si la receta puede ir en la franja: apta para ella ({@code franjasAptas}) y {@link #esCompatible}. */
    public static boolean esCandidata(Receta receta, ContextoGeneracion contexto, TipoComida franja) {
        return receta.getFranjasAptas().contains(franja.name()) && esCompatible(receta, contexto);
    }

    /**
     * Filtro duro salvo la franja: publicada, cálculo COMPLETO o APROXIMADO (con nutrientes por ración), compatible
     * con las restricciones estrictas ({@link CompatibilidadReceta}: ingredientes sin avisos y, con alergias,
     * alérgenos verificados y sin los suyos) y apta para sus dietas y su intolerancia según los datos de la receta,
     * y con un tiempo de preparación que no supere el máximo del perfil (una receta sin tiempo conocido no se
     * descarta).
     */
    public static boolean esCompatible(Receta receta, ContextoGeneracion contexto) {
        if (!receta.isPublicada()) {
            return false;
        }
        if (receta.getEstadoNutricional() != EstadoNutricional.COMPLETO
                && receta.getEstadoNutricional() != EstadoNutricional.APROXIMADO) {
            return false;
        }
        if (receta.getKcalRacion() == null || receta.getProteinaRacion() == null
                || receta.getCarbohidratosRacion() == null || receta.getGrasaRacion() == null) {
            return false;
        }
        if (contexto.tiempoMaxMin() != null && receta.getTiempoPreparacionMin() != null
                && receta.getTiempoPreparacionMin() > contexto.tiempoMaxMin()) {
            return false;
        }
        return cumpleRestricciones(receta, contexto.restricciones());
    }

    /**
     * Solo las restricciones estrictas: {@link CompatibilidadReceta} (con alergias, además alérgenos verificados) y
     * las aptitudes de la receta para las dietas y la intolerancia del usuario. Es la compatibilidad que se exige
     * también al elegir una receta a mano.
     */
    public static boolean cumpleRestricciones(Receta receta, RestriccionesUsuario restricciones) {
        if (restricciones == null) {
            return true;
        }
        if (!restricciones.alergenos().isEmpty() && !receta.isAlergenosVerificados()) {
            return false;
        }
        if (!CompatibilidadReceta.compatible(receta, restricciones)) {
            return false;
        }
        // Las aptitudes de la receta exigen todos los ingredientes emparejados: más estricto que los avisos
        if (restricciones.dietas().contains(Dieta.VEGANA) && !receta.isAptoVegano()) {
            return false;
        }
        if (restricciones.dietas().contains(Dieta.VEGETARIANA) && !receta.isAptoVegetariano()) {
            return false;
        }
        if (restricciones.dietas().contains(Dieta.PESCETARIANA) && !receta.isAptoPescetariano()) {
            return false;
        }
        return !(restricciones.intoleranciaLactosa() && receta.isContieneLactosa());
    }

    /** Si alguna receta puede ir en alguna franja del reparto del usuario. */
    public static boolean hayCandidatas(ContextoGeneracion contexto, List<Receta> candidatas) {
        Set<TipoComida> franjas = RepartoFranjas.de(contexto.comidasPorDia()).keySet();
        return candidatas.stream().anyMatch(receta -> franjas.stream()
                .anyMatch(franja -> esCandidata(receta, contexto, franja)));
    }

    // ---------------------------------------------------------------------------------------------
    // Generación de un día
    // ---------------------------------------------------------------------------------------------

    /**
     * @param objetivoDia     objetivo del día (con el gasto de los entrenamientos)
     * @param diaEntreno      si hay entrenamientos ese día (etiquetas pre-entreno y post-entreno)
     * @param candidatas      recetas públicas o propias del usuario; el motor aplica el resto del filtro duro
     * @param usadasRecientes recetas usadas en los 2 días anteriores (+25)
     * @param bloqueadas      comidas fijadas por el usuario; ocupan su franja
     */
    public ResultadoDia generarDia(ContextoGeneracion contexto, Nutrientes objetivoDia, boolean diaEntreno,
            List<Receta> candidatas, Set<UUID> usadasRecientes, List<SeleccionFranja> bloqueadas) {
        LinkedHashMap<TipoComida, BigDecimal> reparto = RepartoFranjas.de(contexto.comidasPorDia());
        List<SeleccionFranja> fijas = (bloqueadas == null ? List.<SeleccionFranja>of() : bloqueadas).stream()
                .map(s -> s.bloqueada() ? s : new SeleccionFranja(s.tipoComida(), s.receta(), s.raciones(), true))
                .toList();
        Set<UUID> recientes = usadasRecientes == null ? Set.of() : usadasRecientes;

        Nutrientes energiaBloqueada = fijas.stream().map(SeleccionFranja::nutrientes)
                .reduce(Nutrientes.CERO, Nutrientes::sumar);
        Set<TipoComida> tiposBloqueados = new HashSet<>();
        Set<Receta> recetasBloqueadas = new HashSet<>();
        fijas.forEach(s -> {
            tiposBloqueados.add(s.tipoComida());
            recetasBloqueadas.add(s.receta());
        });

        List<String> avisos = new ArrayList<>();
        List<Franja> libres = new ArrayList<>();
        for (Map.Entry<TipoComida, BigDecimal> franja : reparto.entrySet()) {
            if (tiposBloqueados.contains(franja.getKey())) {
                continue;
            }
            List<Receta> aptas = candidatas.stream()
                    .filter(receta -> esCandidata(receta, contexto, franja.getKey()))
                    .filter(receta -> !recetasBloqueadas.contains(receta))
                    .distinct()
                    .sorted(ORDEN_ESTABLE)
                    .toList();
            if (aptas.isEmpty()) {
                avisos.add("No hay recetas aptas para " + nombre(franja.getKey()) + ": la franja queda vacía");
            } else {
                libres.add(new Franja(franja.getKey(), franja.getValue(), aptas));
            }
        }

        Busqueda busqueda = new Busqueda(contexto, objetivoDia, energiaBloqueada, diaEntreno, recientes, libres);
        busqueda.buscar(0, Nutrientes.CERO, new ArrayList<>(), new HashSet<>());

        List<SeleccionFranja> selecciones = ordenar(reparto, fijas, busqueda.mejor);
        Nutrientes total = selecciones.stream().map(SeleccionFranja::nutrientes)
                .reduce(Nutrientes.CERO, Nutrientes::sumar)
                .redondear();
        if (!dentroDeTolerancia(total, objetivoDia)) {
            avisos.add(avisoFueraDeTolerancia(total, objetivoDia));
        }
        return new ResultadoDia(selecciones, total, avisos);
    }

    /** Si el total queda en ±10 % de las kcal y ±15 % de la proteína del objetivo. */
    public static boolean dentroDeTolerancia(Nutrientes total, Nutrientes objetivo) {
        return dentro(total.kcal(), objetivo.kcal(), TOLERANCIA_KCAL_PCT)
                && dentro(total.proteina(), objetivo.proteina(), TOLERANCIA_PROTEINA_PCT);
    }

    private static boolean dentro(BigDecimal valor, BigDecimal objetivo, BigDecimal toleranciaPct) {
        if (objetivo.signum() <= 0) {
            return true;
        }
        BigDecimal margen = objetivo.multiply(toleranciaPct).divide(CIEN, MathContext.DECIMAL64);
        return valor.subtract(objetivo).abs().compareTo(margen) <= 0;
    }

    // ---------------------------------------------------------------------------------------------

    /** Búsqueda en profundidad por franjas: primero la mejor opción de cada una (voraz), después las otras 4. */
    private static final class Busqueda {

        private final ContextoGeneracion contexto;
        private final Nutrientes objetivoDia;
        private final Nutrientes energiaBloqueada;
        private final Nutrientes objetivoLibre;
        private final boolean diaEntreno;
        private final Set<UUID> usadasRecientes;
        private final List<Franja> libres;

        private int nodos;
        private boolean encontrada;
        private List<SeleccionFranja> mejor = List.of();
        private double mejorDesviacion = Double.MAX_VALUE;

        Busqueda(ContextoGeneracion contexto, Nutrientes objetivoDia, Nutrientes energiaBloqueada, boolean diaEntreno,
                Set<UUID> usadasRecientes, List<Franja> libres) {
            this.contexto = contexto;
            this.objetivoDia = objetivoDia;
            this.energiaBloqueada = energiaBloqueada;
            this.objetivoLibre = restarSinNegativos(objetivoDia, energiaBloqueada);
            this.diaEntreno = diaEntreno;
            this.usadasRecientes = usadasRecientes;
            this.libres = libres;
        }

        void buscar(int indice, Nutrientes acumulado, List<SeleccionFranja> camino, Set<Receta> usadasHoy) {
            if (encontrada) {
                return;
            }
            if (indice == libres.size()) {
                evaluar(acumulado, camino);
                return;
            }
            Franja franja = libres.get(indice);
            for (Opcion opcion : opciones(franja, objetivoFranja(indice, acumulado), usadasHoy)) {
                if (encontrada || nodos >= MAXIMO_NODOS) {
                    return;
                }
                nodos++;
                camino.add(new SeleccionFranja(franja.tipo(), opcion.receta(), opcion.raciones(), false));
                boolean nueva = usadasHoy.add(opcion.receta());
                buscar(indice + 1, acumulado.sumar(opcion.aporte()), camino, usadasHoy);
                camino.remove(camino.size() - 1);
                if (nueva) {
                    usadasHoy.remove(opcion.receta());
                }
            }
        }

        /** Lo que falta del día repartido entre las franjas que quedan según su porcentaje. */
        private Nutrientes objetivoFranja(int indice, Nutrientes acumulado) {
            BigDecimal porcentajeRestante = BigDecimal.ZERO;
            for (int i = indice; i < libres.size(); i++) {
                porcentajeRestante = porcentajeRestante.add(libres.get(i).porcentaje());
            }
            BigDecimal proporcion = libres.get(indice).porcentaje().divide(porcentajeRestante, MathContext.DECIMAL64);
            return restarSinNegativos(objetivoLibre, acumulado).escalar(proporcion);
        }

        /** Las 5 mejores (menor puntuación); sin repetir receta en el día salvo que no quede otra. */
        private List<Opcion> opciones(Franja franja, Nutrientes objetivoFranja, Set<Receta> usadasHoy) {
            List<Receta> disponibles = franja.aptas().stream().filter(receta -> !usadasHoy.contains(receta)).toList();
            if (disponibles.isEmpty()) {
                disponibles = franja.aptas();
            }
            List<Opcion> opciones = new ArrayList<>(disponibles.size());
            for (Receta receta : disponibles) {
                BigDecimal raciones = PuntuadorRecetas.mejoresRaciones(receta, objetivoFranja);
                double puntuacion = PuntuadorRecetas.puntuar(receta, raciones, objetivoFranja, contexto,
                        usadasRecientes, diaEntreno, franja.tipo());
                opciones.add(new Opcion(receta, raciones,
                        PuntuadorRecetas.nutrientesPorRacion(receta).escalar(raciones), puntuacion));
            }
            // Orden estable: la lista de aptas ya lo está y List.sort es estable
            opciones.sort(Comparator.comparingDouble(Opcion::puntuacion));
            return opciones.subList(0, Math.min(CANDIDATAS_POR_FRANJA, opciones.size()));
        }

        private void evaluar(Nutrientes acumulado, List<SeleccionFranja> camino) {
            Nutrientes total = energiaBloqueada.sumar(acumulado);
            if (dentroDeTolerancia(total, objetivoDia)) {
                encontrada = true;
                mejor = List.copyOf(camino);
                return;
            }
            double desviacion = PuntuadorRecetas.ajuste(total, objetivoDia);
            if (desviacion < mejorDesviacion) {
                mejorDesviacion = desviacion;
                mejor = List.copyOf(camino);
            }
        }
    }

    /** Las selecciones en el orden de las franjas; las bloqueadas de franjas fuera del reparto, al final. */
    private static List<SeleccionFranja> ordenar(LinkedHashMap<TipoComida, BigDecimal> reparto,
            List<SeleccionFranja> fijas, List<SeleccionFranja> elegidas) {
        List<SeleccionFranja> ordenadas = new ArrayList<>();
        for (TipoComida franja : reparto.keySet()) {
            fijas.stream().filter(s -> s.tipoComida() == franja).forEach(ordenadas::add);
            elegidas.stream().filter(s -> s.tipoComida() == franja).forEach(ordenadas::add);
        }
        fijas.stream().filter(s -> !reparto.containsKey(s.tipoComida())).forEach(ordenadas::add);
        return ordenadas;
    }

    private static Nutrientes restarSinNegativos(Nutrientes a, Nutrientes b) {
        return new Nutrientes(a.kcal().subtract(b.kcal()).max(BigDecimal.ZERO),
                a.proteina().subtract(b.proteina()).max(BigDecimal.ZERO),
                a.carbohidratos().subtract(b.carbohidratos()).max(BigDecimal.ZERO),
                a.grasa().subtract(b.grasa()).max(BigDecimal.ZERO));
    }

    private static String avisoFueraDeTolerancia(Nutrientes total, Nutrientes objetivo) {
        return String.format(ES, "No se ha encontrado una combinación dentro de ±10 %% de energía y ±15 %% de "
                        + "proteína; se propone la más cercana: %s kcal de %s (%s %%) y %s g de proteína de %s (%s %%)",
                entero(total.kcal()), entero(objetivo.kcal()), diferenciaPct(total.kcal(), objetivo.kcal()),
                entero(total.proteina()), entero(objetivo.proteina()),
                diferenciaPct(total.proteina(), objetivo.proteina()));
    }

    private static String entero(BigDecimal valor) {
        return valor.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String diferenciaPct(BigDecimal valor, BigDecimal objetivo) {
        if (objetivo.signum() <= 0) {
            return "—";
        }
        BigDecimal pct = valor.subtract(objetivo).multiply(CIEN).divide(objetivo, 1, RoundingMode.HALF_UP);
        return String.format(ES, "%+.1f", pct);
    }

    /** Nombre de la franja para los avisos. */
    static String nombre(TipoComida franja) {
        return switch (franja) {
            case DESAYUNO -> "el desayuno";
            case MEDIA_MANANA -> "la media mañana";
            case COMIDA -> "la comida";
            case MERIENDA -> "la merienda";
            case CENA -> "la cena";
            case PRE_ENTRENO -> "el pre-entreno";
            case POST_ENTRENO -> "el post-entreno";
            case OTRO -> "otra comida";
        };
    }
}
