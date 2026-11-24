package com.nutrisport.comidas.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutrisport.almacenamiento.AlmacenamientoProperties;
import com.nutrisport.almacenamiento.AlmacenamientoServicio;
import com.nutrisport.comidas.entidad.AnalisisImagen;
import com.nutrisport.comidas.repositorio.AnalisisImagenRepositorio;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class LimpiezaImagenesTareaTest {

    @Test
    void procesaEnLotesDeDoscientosYBorraLasClavesDeCadaAnalisis() {
        AnalisisImagenRepositorio repositorio = mock(AnalisisImagenRepositorio.class);
        AlmacenamientoServicio almacenamiento = mock(AlmacenamientoServicio.class);
        Clock reloj = Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneId.of("Europe/Madrid"));
        AlmacenamientoProperties propiedades = new AlmacenamientoProperties(
                URI.create("http://localhost:9000"), URI.create("http://localhost:9000"),
                "us-east-1", "fotos", "usuario", "clave", 5, 90);
        LimpiezaImagenesTarea tarea = new LimpiezaImagenesTarea(repositorio, almacenamiento, propiedades, reloj);

        List<AnalisisImagen> primerLote = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            primerLote.add(analisisConClaves());
        }
        AnalisisImagen ultimo = analisisConClaves();
        when(repositorio.buscarParaLimpieza(any(OffsetDateTime.class), any(OffsetDateTime.class),
                any(), any(Pageable.class))).thenReturn(primerLote, List.of(ultimo), List.of());

        int eliminados = tarea.ejecutar(LocalDate.of(2026, 11, 24));

        assertThat(eliminados).isEqualTo(402);
        assertThat(primerLote).allSatisfy(analisis -> {
            assertThat(analisis.getImagenKey()).isNull();
            assertThat(analisis.getMiniaturaKey()).isNull();
        });
        assertThat(ultimo.getImagenKey()).isNull();
        assertThat(ultimo.getMiniaturaKey()).isNull();
        verify(almacenamiento, times(402)).eliminar(anyString());
        verify(repositorio, times(2)).flush();

        ArgumentCaptor<Pageable> paginacion = ArgumentCaptor.forClass(Pageable.class);
        verify(repositorio, times(3)).buscarParaLimpieza(any(OffsetDateTime.class),
                any(OffsetDateTime.class), any(), paginacion.capture());
        assertThat(paginacion.getAllValues()).allSatisfy(pagina -> assertThat(pagina.getPageSize()).isEqualTo(200));
    }

    private static AnalisisImagen analisisConClaves() {
        AnalisisImagen analisis = new AnalisisImagen();
        String identificador = UUID.randomUUID().toString();
        analisis.setImagenKey("usuarios/prueba/" + identificador + ".jpg");
        analisis.setMiniaturaKey("usuarios/prueba/" + identificador + "_min.jpg");
        return analisis;
    }
}
