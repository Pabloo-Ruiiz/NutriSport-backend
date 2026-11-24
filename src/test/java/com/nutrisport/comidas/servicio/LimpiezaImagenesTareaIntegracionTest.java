package com.nutrisport.comidas.servicio;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutrisport.IntegracionBase;
import com.nutrisport.almacenamiento.AlmacenamientoServicio;
import com.nutrisport.almacenamiento.ClavesAlmacenamiento;
import com.nutrisport.auth.entidad.Usuario;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class LimpiezaImagenesTareaIntegracionTest extends IntegracionBase {

    private final LimpiezaImagenesTarea tarea;
    private final AlmacenamientoServicio almacenamiento;
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    private Usuario usuario;

    @Autowired
    LimpiezaImagenesTareaIntegracionTest(LimpiezaImagenesTarea tarea,
            AlmacenamientoServicio almacenamiento, JdbcTemplate jdbc, Clock reloj) {
        this.tarea = tarea;
        this.almacenamiento = almacenamiento;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE usuario CASCADE");
        usuario = crearUsuarioYToken().usuario();
    }

    @AfterEach
    void limpiar() {
        almacenamiento.eliminarPrefijo(ClavesAlmacenamiento.prefijoUsuario(usuario.getId()));
        jdbc.update("DELETE FROM usuario WHERE id = ?", usuario.getId());
    }

    @Test
    void aplicaRetencionGeneralYRetencionCortaDesdeElCambioAEstadoTerminal() {
        LocalDate hoy = LocalDate.now(reloj);
        ZoneId zona = reloj.getZone();
        UUID antiguo = guardarAnalisis("COMPLETADO", hoy.minusDays(91), hoy.minusDays(91), zona);
        UUID descartado = guardarAnalisis("DESCARTADO", hoy.minusDays(10), hoy.minusDays(8), zona);
        UUID fallido = guardarAnalisis("FALLIDO", hoy.minusDays(10), hoy.minusDays(8), zona);
        UUID fallidoReciente = guardarAnalisis("FALLIDO", hoy.minusDays(10), hoy.minusDays(7), zona);
        UUID completadoReciente = guardarAnalisis("COMPLETADO", hoy.minusDays(8), hoy.minusDays(8), zona);

        assertThat(tarea.ejecutar(hoy)).isEqualTo(6);

        assertThat(clavesNulas(antiguo)).isTrue();
        assertThat(clavesNulas(descartado)).isTrue();
        assertThat(clavesNulas(fallido)).isTrue();
        assertThat(clavesNulas(fallidoReciente)).isFalse();
        assertThat(clavesNulas(completadoReciente)).isFalse();
    }

    private UUID guardarAnalisis(String estado, LocalDate fechaCreacion, LocalDate fechaActualizacion, ZoneId zona) {
        UUID id = UUID.randomUUID();
        String prefijo = "usuarios/" + usuario.getId() + "/prueba/" + id;
        String imagen = prefijo + ".jpg";
        String miniatura = prefijo + "_min.jpg";
        byte[] contenido = new byte[] {1, 2, 3};
        almacenamiento.guardar(imagen, contenido, "image/jpeg");
        almacenamiento.guardar(miniatura, contenido, "image/jpeg");

        jdbc.update("""
                INSERT INTO analisis_imagen
                    (id, usuario_id, tipo, imagen_key, miniatura_key, estado, creado_en, actualizado_en)
                VALUES (?, ?, 'COMIDA', ?, ?, ?, ?, ?)
                """, id, usuario.getId(), imagen, miniatura, estado,
                fecha(fechaCreacion, zona), fecha(fechaActualizacion, zona));
        return id;
    }

    private boolean clavesNulas(UUID id) {
        return jdbc.queryForObject("""
                SELECT imagen_key IS NULL AND miniatura_key IS NULL
                FROM analisis_imagen WHERE id = ?
                """, Boolean.class, id);
    }

    private static OffsetDateTime fecha(LocalDate fecha, ZoneId zona) {
        return fecha.atStartOfDay(zona).plusHours(12).toOffsetDateTime();
    }
}
