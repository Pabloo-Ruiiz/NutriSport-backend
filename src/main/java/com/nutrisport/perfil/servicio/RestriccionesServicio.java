package com.nutrisport.perfil.servicio;

import com.nutrisport.perfil.entidad.Dieta;
import com.nutrisport.perfil.entidad.PreferenciaAlimentaria;
import com.nutrisport.perfil.entidad.Severidad;
import com.nutrisport.perfil.repositorio.PreferenciaAlimentariaRepositorio;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Construye las restricciones estrictas de un usuario a partir de sus preferencias (§3.B). */
@Service
@Transactional(readOnly = true)
public class RestriccionesServicio {

    private final PreferenciaAlimentariaRepositorio preferenciaRepositorio;

    public RestriccionesServicio(PreferenciaAlimentariaRepositorio preferenciaRepositorio) {
        this.preferenciaRepositorio = preferenciaRepositorio;
    }

    /** Solo las preferencias de severidad ESTRICTA; las de PREFERENCIA no generan avisos. */
    public RestriccionesUsuario estrictasDe(UUID usuarioId) {
        Set<String> alergenos = new HashSet<>();
        boolean lactosa = false;
        Set<Dieta> dietas = new HashSet<>();
        Set<UUID> alimentosRechazados = new HashSet<>();
        Set<UUID> categoriasRechazadas = new HashSet<>();

        List<PreferenciaAlimentaria> estrictas = preferenciaRepositorio.findByUsuarioId(usuarioId).stream()
                .filter(preferencia -> preferencia.getSeveridad() == Severidad.ESTRICTA)
                .toList();
        for (PreferenciaAlimentaria preferencia : estrictas) {
            switch (preferencia.getTipo()) {
                case ALERGIA -> alergenos.add(preferencia.getAlergeno().name());
                case INTOLERANCIA -> lactosa = true; // LACTOSA es la única intolerancia
                case DIETA -> dietas.add(preferencia.getDieta());
                case RECHAZO -> {
                    if (preferencia.getAlimentoId() != null) {
                        alimentosRechazados.add(preferencia.getAlimentoId());
                    }
                    if (preferencia.getCategoriaId() != null) {
                        categoriasRechazadas.add(preferencia.getCategoriaId());
                    }
                }
                case FAVORITO -> {
                    // Los favoritos no restringen nada
                }
            }
        }
        return new RestriccionesUsuario(alergenos, lactosa, dietas, alimentosRechazados, categoriasRechazadas);
    }
}
