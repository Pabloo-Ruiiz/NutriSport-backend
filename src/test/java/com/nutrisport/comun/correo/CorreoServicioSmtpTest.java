package com.nutrisport.comun.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(OutputCaptureExtension.class)
class CorreoServicioSmtpTest {

    private static final String TOKEN = "TokenSecretoDePrueba_abc123";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final CorreoServicioSmtp servicio = new CorreoServicioSmtp(mailSender, "http://localhost:4200/");

    @Test
    void verificacionLlevaElTokenEnElFragmento() {
        servicio.enviarVerificacion("ana@ejemplo.com", "Ana", TOKEN);

        SimpleMailMessage mensaje = mensajeEnviado();
        assertThat(mensaje.getFrom()).isEqualTo("NutriSport AI <no-reply@nutrisport.local>");
        assertThat(mensaje.getTo()).containsExactly("ana@ejemplo.com");
        assertThat(mensaje.getSubject()).isEqualTo("Verifica tu email en NutriSport AI");
        assertThat(mensaje.getText())
                .startsWith("Hola, Ana:")
                .contains("http://localhost:4200/verificar-email#token=" + TOKEN)
                .contains("24 horas")
                .doesNotContain("?token=");
    }

    @Test
    void recuperacionYCambioDeEmailApuntanASusPantallas() {
        servicio.enviarRecuperacion("ana@ejemplo.com", "Ana", TOKEN);
        assertThat(mensajeEnviado().getText())
                .contains("http://localhost:4200/restablecer-password#token=" + TOKEN)
                .contains("30 minutos");

        JavaMailSender otroSender = mock(JavaMailSender.class);
        new CorreoServicioSmtp(otroSender, "http://localhost:4200")
                .enviarCambioEmail("nuevo@ejemplo.com", "Ana", TOKEN);
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(otroSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("nuevo@ejemplo.com");
        assertThat(captor.getValue().getText()).contains("http://localhost:4200/verificar-email#token=" + TOKEN);
    }

    @Test
    void avisoDeCuentaExistenteNoLlevaToken() {
        servicio.enviarAvisoCuentaExistente("ana@ejemplo.com");

        assertThat(mensajeEnviado().getText()).contains("ya tienes una").doesNotContain("token");
    }

    @Test
    void unFalloDeEnvioNoSePropagaNiRegistraElToken(CapturedOutput salida) {
        doThrow(new MailSendException("Fallo SMTP con " + TOKEN)).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> servicio.enviarRecuperacion("ana@ejemplo.com", "Ana", TOKEN)).doesNotThrowAnyException();

        assertThat(salida.getAll())
                .contains("No se pudo enviar el correo de recuperación de contraseña")
                .doesNotContain(TOKEN)
                .doesNotContain("ana@ejemplo.com");
    }

    private SimpleMailMessage mensajeEnviado() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }
}
