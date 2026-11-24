package com.nutrisport.perfil.mapper;

import com.nutrisport.perfil.dto.PerfilRequest;
import com.nutrisport.perfil.dto.PerfilResponse;
import com.nutrisport.perfil.dto.PreferenciaDto;
import com.nutrisport.perfil.entidad.PerfilDeportivo;
import com.nutrisport.perfil.entidad.PreferenciaAlimentaria;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Conversiones del perfil y las preferencias. Falla al compilar si queda algún campo sin mapear. El
 * usuario lo asigna siempre el servicio y los objetivos los gestiona su propio cálculo (paso 12).
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PerfilMapper {

    @Mapping(target = "pesoKg", source = "pesoActualKg")
    PerfilResponse aRespuesta(PerfilDeportivo perfil);

    /** Copia los datos editables de la petición sobre el perfil (nuevo o existente). */
    @Mapping(target = "pesoActualKg", source = "pesoKg")
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "objetivoKcal", ignore = true)
    @Mapping(target = "objetivoProteinaG", ignore = true)
    @Mapping(target = "objetivoCarbohidratosG", ignore = true)
    @Mapping(target = "objetivoGrasaG", ignore = true)
    @Mapping(target = "objetivosManuales", ignore = true)
    void actualizar(PerfilRequest peticion, @MappingTarget PerfilDeportivo perfil);

    PreferenciaDto aDto(PreferenciaAlimentaria preferencia);

    List<PreferenciaDto> aDtos(List<PreferenciaAlimentaria> preferencias);

    @Mapping(target = "usuario", ignore = true)
    PreferenciaAlimentaria aEntidad(PreferenciaDto dto);
}
