package com.nutrisport.auth.mapper;

import com.nutrisport.auth.dto.CuentaResponse;
import com.nutrisport.auth.entidad.Usuario;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Usuario → CuentaResponse. Falla al compilar si queda algún campo de la respuesta sin mapear. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CuentaMapper {

    @Mapping(target = "emailVerificado", expression = "java(usuario.emailVerificado())")
    @Mapping(target = "consentimientoSaludVigente", expression = "java(usuario.consentimientoVigente())")
    CuentaResponse aRespuesta(Usuario usuario);
}
