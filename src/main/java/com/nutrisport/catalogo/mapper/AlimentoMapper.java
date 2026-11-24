package com.nutrisport.catalogo.mapper;

import com.nutrisport.catalogo.dto.AlimentoRequest;
import com.nutrisport.catalogo.dto.AlimentoResponse;
import com.nutrisport.catalogo.dto.AlimentoResumenResponse;
import com.nutrisport.catalogo.entidad.Alimento;
import java.util.UUID;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * Conversiones de alimentos. Los alérgenos se guardan como texto (constantes de Alergeno) y en la
 * API viajan como enum. La categoría, el propietario, la fuente y los indicadores de revisión los
 * asigna siempre el servicio. Falla al compilar si queda algún campo sin mapear.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AlimentoMapper {

    /** {@code usuarioId} es el usuario autenticado, para calcular {@code propio}. */
    @Mapping(target = "categoriaId", source = "alimento.categoria.id")
    @Mapping(target = "categoriaNombre", source = "alimento.categoria.nombre")
    @Mapping(target = "propio", expression = "java(alimento.esPrivadoDe(usuarioId))")
    AlimentoResponse aRespuesta(Alimento alimento, @Context UUID usuarioId);

    AlimentoResumenResponse aResumen(Alimento alimento);

    /** Copia los datos editables de la petición sobre el alimento (nuevo o existente). */
    @Mapping(target = "preparacion", source = "preparacion", defaultValue = "OTRA")
    @Mapping(target = "categoria", ignore = true)
    @Mapping(target = "codigoBarras", ignore = true)
    @Mapping(target = "alergenosRevisados", ignore = true)
    @Mapping(target = "fuente", ignore = true)
    @Mapping(target = "referenciaExterna", ignore = true)
    @Mapping(target = "visibilidad", ignore = true)
    @Mapping(target = "propietario", ignore = true)
    @Mapping(target = "validado", ignore = true)
    @Mapping(target = "activo", ignore = true)
    void actualizar(AlimentoRequest peticion, @MappingTarget Alimento alimento);
}
