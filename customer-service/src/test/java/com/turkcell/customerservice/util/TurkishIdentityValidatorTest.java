package com.turkcell.customerservice.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** TCKN/VKN algoritmik dogrulama (FR-01). */
class TurkishIdentityValidatorTest {

    @Test
    void validTcknPassesChecksum() {
        assertThat(TurkishIdentityValidator.isValidTckn("10000000146")).isTrue();
    }

    @Test
    void invalidTcknIsRejected() {
        assertThat(TurkishIdentityValidator.isValidTckn("12345678901")).isFalse(); // checksum tutmaz
        assertThat(TurkishIdentityValidator.isValidTckn("00000000146")).isFalse(); // ilk hane 0
        assertThat(TurkishIdentityValidator.isValidTckn("1000000014")).isFalse();  // 10 hane
        assertThat(TurkishIdentityValidator.isValidTckn("100000001466")).isFalse(); // 12 hane
        assertThat(TurkishIdentityValidator.isValidTckn("1000000014a")).isFalse(); // harf
        assertThat(TurkishIdentityValidator.isValidTckn(null)).isFalse();
    }

    @Test
    void validVknPassesChecksum() {
        assertThat(TurkishIdentityValidator.isValidVkn("1111111114")).isTrue();
    }

    @Test
    void invalidVknIsRejected() {
        assertThat(TurkishIdentityValidator.isValidVkn("1111111111")).isFalse(); // checksum tutmaz
        assertThat(TurkishIdentityValidator.isValidVkn("111111111")).isFalse();  // 9 hane
        assertThat(TurkishIdentityValidator.isValidVkn("11111111145")).isFalse(); // 11 hane
        assertThat(TurkishIdentityValidator.isValidVkn("111111111a")).isFalse();  // harf
        assertThat(TurkishIdentityValidator.isValidVkn(null)).isFalse();
    }
}
