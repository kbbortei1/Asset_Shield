package com.assetshield.marketplace.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PesewasTest {

    @Test
    void wholeGhsConvertsExactly() {
        assertThat(PaymentProvider.toPesewas(new BigDecimal("50.00"))).isEqualTo(5000L);
        assertThat(PaymentProvider.toPesewas(new BigDecimal("50"))).isEqualTo(5000L);
    }

    @Test
    void fractionalGhsConvertsExactly() {
        assertThat(PaymentProvider.toPesewas(new BigDecimal("49.99"))).isEqualTo(4999L);
        assertThat(PaymentProvider.toPesewas(new BigDecimal("0.01"))).isEqualTo(1L);
        assertThat(PaymentProvider.toPesewas(new BigDecimal("123.45"))).isEqualTo(12345L);
    }

    @Test
    void synthesizedEmailUsesPhoneDigits() {
        assertThat(PaymentService.synthesizeEmail("+233244123456"))
                .isEqualTo("233244123456@assetshield.app");
    }
}
