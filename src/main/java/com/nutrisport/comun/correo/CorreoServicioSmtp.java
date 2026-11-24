package com.nutrisport.comun.correo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envía los correos de la cuenta por SMTP (Mailpit en desarrollo) como texto plano en castellano.
 * <p>
 * Los enlaces llevan el token en el fragmento ({@code #token=...}): el navegador no envía el
 * fragmento al servidor, así que el token no queda en los logs de acceso ni en la cabecera Referer.
 * <p>
 * Si el envío falla se registra el error sin el token ni el contenido del correo y no se propaga:
 * el usuario puede pedir el reenvío.
 */
@Service
public class CorreoServicioSmtp implements CorreoServicio {

    private static final Logger LOG = LoggerFactory.getLogger(CorreoServicioSmtp.class);

    static final String REMITENTE = "NutriSport AI <no-reply@nutrisport.local>";

    private static final String FIRMA = """

            Si no has sido tú, puedes ignorar este mensaje.

            El equipo de NutriSport AI
            """;

    private final JavaMailSender mailSender;
    private final String urlFrontend;

    public CorreoServicioSmtp(JavaMailSender mailSender, @Value("${app.frontend-url}") String urlFrontend) {
        this.mailSender = mailSender;
        // Sin barra final para no generar "//" en los enlaces
        this.urlFrontend = urlFrontend.endsWith("/") ? urlFrontend.substring(0, urlFrontend.length() - 1) : urlFrontend;
    }

    @Override
    public void enviarVerificacion(String email, String nombre, String token) {
        String texto = """
                Hola, %s:

                Gracias por registrarte en NutriSport AI. Para verificar tu email, abre este enlace:

                %s

                El enlace es válido durante 24 horas y solo se puede usar una vez.
                """.formatted(nombre, enlace("/verificar-email", token)) + FIRMA;
        enviar("verificación de email", email, "Verifica tu email en NutriSport AI", texto);
    }

    @Override
    public void enviarAvisoCuentaExistente(String email) {
        String texto = """
                Hola:

                Alguien ha intentado crear una cuenta en NutriSport AI con este email, pero ya tienes una.

                Si has sido tú, inicia sesión en %s
                Si no recuerdas tu contraseña, puedes restablecerla desde la pantalla de inicio de sesión.
                """.formatted(urlFrontend) + FIRMA;
        enviar("aviso de cuenta existente", email, "Ya tienes una cuenta en NutriSport AI", texto);
    }

    @Override
    public void enviarRecuperacion(String email, String nombre, String token) {
        String texto = """
                Hola, %s:

                Hemos recibido una solicitud para restablecer tu contraseña. Para elegir una nueva, abre este enlace:

                %s

                El enlace es válido durante 30 minutos y solo se puede usar una vez.
                Al cambiar la contraseña se cerrarán tus demás sesiones.
                """.formatted(nombre, enlace("/restablecer-password", token)) + FIRMA;
        enviar("recuperación de contraseña", email, "Restablece tu contraseña de NutriSport AI", texto);
    }

    @Override
    public void enviarCambioEmail(String nuevoEmail, String nombre, String token) {
        String texto = """
                Hola, %s:

                Has pedido usar esta dirección como nuevo email de tu cuenta de NutriSport AI.
                Para confirmarlo, abre este enlace:

                %s

                El enlace es válido durante 24 horas y solo se puede usar una vez.
                Hasta que lo confirmes, seguirás usando tu email anterior.
                """.formatted(nombre, enlace("/verificar-email", token)) + FIRMA;
        enviar("confirmación de cambio de email", nuevoEmail, "Confirma tu nuevo email en NutriSport AI", texto);
    }

    /** El token va en el fragmento, nunca en la ruta ni en la query. */
    private String enlace(String ruta, String token) {
        return urlFrontend + ruta + "#token=" + token;
    }

    private void enviar(String tipo, String destinatario, String asunto, String texto) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(REMITENTE);
        mensaje.setTo(destinatario);
        mensaje.setSubject(asunto);
        mensaje.setText(texto);
        try {
            mailSender.send(mensaje);
        } catch (MailException e) {
            // Sin la excepción completa: su mensaje puede incluir el destinatario o el contenido (con el token)
            LOG.error("No se pudo enviar el correo de {} ({})", tipo, e.getClass().getSimpleName());
        }
    }
}
