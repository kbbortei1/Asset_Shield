package com.assetshield.property.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Sha256Test {

    @Test
    void hashesToLowercaseHexOfKnownVector() {
        // SHA-256("abc") — FIPS 180 test vector
        assertThat(Sha256.hex("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void matchesIsCaseInsensitive() {
        byte[] bytes = "evidence".getBytes(StandardCharsets.UTF_8);
        String hex = Sha256.hex(bytes);
        assertThat(Sha256.matches(bytes, hex)).isTrue();
        assertThat(Sha256.matches(bytes, hex.toUpperCase())).isTrue();
    }

    @Test
    void tamperedBytesDoNotMatch() {
        String declared = Sha256.hex("original".getBytes(StandardCharsets.UTF_8));
        assertThat(Sha256.matches("tampered".getBytes(StandardCharsets.UTF_8), declared)).isFalse();
    }
}
