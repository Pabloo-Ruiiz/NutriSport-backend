package com.nutrisport.actividad.mapper;

import com.nutrisport.actividad.dto.EntrenamientoResponse;
import com.nutrisport.actividad.dto.TipoActividadResponse;
import com.nutrisport.actividad.entidad.Entrenamiento;
import com.nutrisport.actividad.entidad.TipoActividad;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Conversiones de entrenamientos y tipos de actividad. Falla al compilar si queda algún campo sin mapear. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EntrenamientoMapper {

    @Mapping(target = "tipoActividadCodigo", source = "tipoActividad.codigo")
    @Mapping(target = "tipoActividadNombre", source = "tipoActividad.nombre")
    EntrenamientoResponse aRespuesta(Entrenamiento entrenamiento);

    TipoActividadResponse aRespuesta(TipoActividad tipo);
}
