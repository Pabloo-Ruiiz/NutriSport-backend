package com.nutrisport.perfil.servicio;

import com.nutrisport.catalogo.entidad.Alimento;
import com.nutrisport.perfil.entidad.Dieta;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Restricciones estrictas de un usuario (preferencias de severidad ESTRICTA, §3.B) y los avisos que
 * generan al registrar un alimento. Los avisos informan, no bloquean (§3.C).
 *
 * @param alergenos            códigos de {@code Alergeno} a los que es alérgico
 * @param intoleranciaLactosa  si es intolerante a la lactosa
 * @param dietas               dietas que sigue (HALAL y KOSHER no se comprueban: son preferencia blanda)
 * @param alimentosRechazados  ids de alimentos rechazados
 * @param categoriasRechazadas ids de categorías rechazadas
 */
public record RestriccionesUsuario(Set<String> alergenos, boolean intoleranciaLactosa, Set<Dieta> dietas,
        Set<UUID> alimentosRechazados, Set<UUID> categoriasRechazadas) {

    public static final RestriccionesUsuario NINGUNA = new RestriccionesUsuario(Set.of(), false, Set.of(), Set.of(),
            Set.of());

    public RestriccionesUsuario {
        alergenos = Set.copyOf(alergenos);
        dietas = Set.copyOf(dietas);
        alimentosRechazados = Set.copyOf(alimentosRechazados);
        categoriasRechazadas = Set.copyOf(categoriasRechazadas);
    }

    /**
     * Las restricciones en palabras, sin datos identificativos ("alergia a gluten, sin lactosa, dieta vegana, 2
     * alimentos o categorías rechazados") o "ninguna". La usan los textos que se envían a la IA (menús, sugerencias y
     * asistente). HALAL y KOSHER no se mencionan: son preferencia blanda.
     */
    public String descripcion() {
        List<String> partes = new ArrayList<>();
        alergenos.stream().sorted()
                .forEach(alergeno -> partes.add("alergia a " + alergeno.toLowerCase(Locale.ROOT).replace('_', ' ')));
        if (intoleranciaLactosa) {
            partes.add("sin lactosa");
        }
        dietas.stream().sorted()
                .filter(dieta -> dieta != Dieta.HALAL && dieta != Dieta.KOSHER)
                .forEach(dieta -> partes.add("dieta " + dieta.name().toLowerCase(Locale.ROOT)));
        int rechazos = alimentosRechazados.size() + categoriasRechazadas.size();
        if (rechazos > 0) {
            partes.add(rechazos + (rechazos == 1 ? " alimento o categoría rechazados"
                    : " alimentos o categorías rechazados"));
        }
        return partes.isEmpty() ? "ninguna" : String.join(", ", partes);
    }

    /** Avisos para el alimento, en este orden: alérgenos, trazas, sin revisar, lactosa, dietas y rechazos. */
    public List<String> avisosPara(Alimento alimento) {
        List<String> avisos = new ArrayList<>();
        alimento.getAlergenosContiene().stream()
                .filter(alergenos::contains)
                .forEach(alergeno -> avisos.add("Contiene " + alergeno));
        alimento.getAlergenosTrazas().stream()
                .filter(alergenos::contains)
                .filter(alergeno -> !alimento.getAlergenosContiene().contains(alergeno))
                .forEach(alergeno -> avisos.add("Puede contener trazas de " + alergeno));
        if (!alergenos.isEmpty() && !alimento.isAlergenosRevisados()) {
            avisos.add("Alérgenos sin revisar");
        }
        if (intoleranciaLactosa && alimento.isContieneLactosa()) {
            avisos.add("Contiene lactosa");
        }
        if (dietas.contains(Dieta.VEGANA) && !alimento.isAptoVegano()) {
            avisos.add("No es apto para una dieta vegana");
        }
        if (dietas.contains(Dieta.VEGETARIANA) && !alimento.isAptoVegetariano()) {
            avisos.add("No es apto para una dieta vegetariana");
        }
        if (dietas.contains(Dieta.PESCETARIANA) && !alimento.isAptoPescetariano()) {
            avisos.add("No es apto para una dieta pescetariana");
        }
        if (alimentosRechazados.contains(alimento.getId())) {
            avisos.add("Es un alimento que has marcado como rechazado");
        }
        // getId() de un proxy perezoso no lo inicializa
        if (alimento.getCategoria() != null && categoriasRechazadas.contains(alimento.getCategoria().getId())) {
            avisos.add("Es de una categoría que has marcado como rechazada");
        }
        return avisos;
    }
}
