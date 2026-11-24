package com.nutrisport.almacenamiento;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Claves de los objetos en el bucket (§5.8). Solo contienen identificadores opacos y la fecha: nunca
 * nombres, emails ni otros datos personales.
 */
public final class ClavesAlmacenamiento {

    private static final String EXTENSION = ".jpg";
    private static final String SUFIJO_MINIATURA = "_min.jpg";

    private ClavesAlmacenamiento() {
    }

    /** {@code usuarios/{usuarioId}/comidas/{yyyy}/{MM}/{uuid aleatorio}.jpg}. */
    public static String claveComida(UUID usuarioId, LocalDate fecha) {
        return "%scomidas/%04d/%02d/%s%s".formatted(prefijoUsuario(usuarioId), fecha.getYear(),
                fecha.getMonthValue(), UUID.randomUUID(), EXTENSION);
    }

    /** Clave de la miniatura de una imagen: {@code ….jpg} → {@code …_min.jpg}. */
    public static String claveMiniatura(String clave) {
        if (clave == null || !clave.endsWith(EXTENSION) || clave.endsWith(SUFIJO_MINIATURA)) {
            throw new IllegalArgumentException("No es la clave de una imagen original: " + clave);
        }
        return clave.substring(0, clave.length() - EXTENSION.length()) + SUFIJO_MINIATURA;
    }

    /** Prefijo de todos los objetos de un usuario, para borrarlos al eliminar su cuenta. */
    public static String prefijoUsuario(UUID usuarioId) {
        return "usuarios/" + usuarioId + "/";
    }
}
