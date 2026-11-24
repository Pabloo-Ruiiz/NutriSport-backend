package com.nutrisport.comidas.mapper;

import com.nutrisport.comidas.dto.AlimentoRegistradoResponse;
import com.nutrisport.comidas.dto.RegistroComidaResponse;
import com.nutrisport.comidas.entidad.AlimentoRegistrado;
import com.nutrisport.comidas.entidad.RegistroComida;
import com.nutrisport.comun.dto.Nutrientes;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** Conversiones de comidas. Los avisos los calcula el servicio. Falla al compilar si queda algún campo sin mapear. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR, imports = Nutrientes.class)
public interface ComidaMapper {

    @Mapping(target = "totales", expression = "java(new Nutrientes(registro.getTotalKcal(), "
            + "registro.getTotalProteina(), registro.getTotalCarbohidratos(), registro.getTotalGrasa()))")
    RegistroComidaResponse aRespuesta(RegistroComida registro, List<String> avisos);

    @Mapping(target = "alimentoId", source = "alimento.id")
    @Mapping(target = "nombre", source = "alimento.nombre")
    AlimentoRegistradoResponse aRespuestaLinea(AlimentoRegistrado linea);
}
