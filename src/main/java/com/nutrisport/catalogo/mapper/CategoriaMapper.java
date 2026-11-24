package com.nutrisport.catalogo.mapper;

import com.nutrisport.catalogo.dto.CategoriaResponse;
import com.nutrisport.catalogo.entidad.Categoria;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Categoria → CategoriaResponse. Falla al compilar si queda algún campo sin mapear. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CategoriaMapper {

    @Mapping(target = "padreId", source = "padre.id")
    CategoriaResponse aRespuesta(Categoria categoria);

    List<CategoriaResponse> aRespuestas(List<Categoria> categorias);
}
