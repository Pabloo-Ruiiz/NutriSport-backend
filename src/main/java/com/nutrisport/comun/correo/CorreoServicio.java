package com.nutrisport.comun.correo;

/**
 * Envío de los correos de la cuenta (§3.A). Las implementaciones no lanzan excepciones si el envío
 * falla: lo registran y la operación que lo pidió continúa.
 */
public interface CorreoServicio {

    /** Enlace para verificar el email tras el registro o al reenviarlo (válido 24 h). */
    void enviarVerificacion(String email, String nombre, String token);

    /**
     * Aviso al titular cuando alguien intenta registrarse con su email. El registro responde igual
     * en ambos casos para no revelar qué emails existen (§3.A).
     */
    void enviarAvisoCuentaExistente(String email);

    /** Enlace para restablecer la contraseña (válido 30 min). */
    void enviarRecuperacion(String email, String nombre, String token);

    /** Enlace, enviado a la nueva dirección, para confirmar el cambio de email. */
    void enviarCambioEmail(String nuevoEmail, String nombre, String token);
}
