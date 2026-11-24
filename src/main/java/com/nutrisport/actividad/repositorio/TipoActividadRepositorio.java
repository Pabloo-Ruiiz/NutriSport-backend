package com.nutrisport.actividad.repositorio;

import com.nutrisport.actividad.entidad.TipoActividad;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la tabla {@code tipo_actividad}; la clave es el código. */
public interface TipoActividadRepositorio extends JpaRepository<TipoActividad, String> {

    /** Catálogo para elegir actividad: solo las activas, por categoría y nombre. */
    List<TipoActividad> findByActivoTrueOrderByCategoriaAscNombreAsc();

    boolean existsByCodigoAndActivoTrue(String codigo);
}
