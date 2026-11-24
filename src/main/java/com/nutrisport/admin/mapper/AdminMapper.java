package com.nutrisport.admin.mapper;

import com.nutrisport.admin.dto.UsuarioAdminResponse;
import com.nutrisport.auth.entidad.Usuario;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Usuario → UsuarioAdminResponse (solo datos de cuenta). Falla al compilar si queda algún campo sin mapear. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AdminMapper {

    @Mapping(target = "emailVerificado", expression = "java(usuario.emailVerificado())")
    UsuarioAdminResponse aRespuesta(Usuario usuario);
}
