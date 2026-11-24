package com.nutrisport.comun.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens opacos de un solo uso (refresh, verificación de email, recuperación de contraseña, §5.5).
 * Al usuario se le entrega el token; en la base de datos solo se guarda su SHA-256.
 */
public final class Tokens {

    /** 256 bits de entropía. */
    private static final int BYTES_TOKEN = 32;

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private Tokens() {
    }

    /** 32 bytes aleatorios en Base64 URL sin relleno (43 caracteres, seguros en URL y cookies). */
    public static String generar() {
        byte[] bytes = new byte[BYTES_TOKEN];
        ALEATORIO.nextBytes(bytes);
        return BASE64_URL.encodeToString(bytes);
    }

    /** SHA-256 del texto (UTF-8) en hexadecimal en minúsculas: 64 caracteres, como la columna char(64). */
    public static String sha256Hex(String texto) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Todas las JVM están obligadas a incluir SHA-256
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM", e);
        }
    }
}
