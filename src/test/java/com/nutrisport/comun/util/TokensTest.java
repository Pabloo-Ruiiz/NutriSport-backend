package com.nutrisport.comun.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokensTest {

    @Test
    void generaTokensDe32BytesEnBase64UrlSinRelleno() {
        String token = Tokens.generar();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
    }

    @Test
    void generaTokensDistintos() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(Tokens.generar());
        }
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void sha256HexEsConocidoYTiene64Caracteres() {
        // Vector de prueba estándar de SHA-256
        assertThat(Tokens.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(Tokens.sha256Hex(Tokens.generar())).hasSize(64).matches("[0-9a-f]+");
    }
}
