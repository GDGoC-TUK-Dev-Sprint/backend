package com.tukorea.gdgoc.koready.global.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DisplayCurrencyTest {

	@Test
	void headerDefaultsToKrwAndIgnoresCase() {
		assertThat(DisplayCurrency.fromHeader(null)).isEqualTo(DisplayCurrency.KRW);
		assertThat(DisplayCurrency.fromHeader(" ")).isEqualTo(DisplayCurrency.KRW);
		assertThat(DisplayCurrency.fromHeader("usd")).isEqualTo(DisplayCurrency.USD);
		assertThat(DisplayCurrency.fromHeader(" JPY ")).isEqualTo(DisplayCurrency.JPY);
	}

	@Test
	void unsupportedHeaderIsRejected() {
		assertThatThrownBy(() -> DisplayCurrency.fromHeader("GBP")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void fractionDigitsPerCurrency() {
		assertThat(DisplayCurrency.KRW.fits(new BigDecimal("1000"))).isTrue();
		assertThat(DisplayCurrency.KRW.fits(new BigDecimal("1000.00"))).isTrue();
		assertThat(DisplayCurrency.KRW.fits(new BigDecimal("1000.5"))).isFalse();
		assertThat(DisplayCurrency.JPY.fits(new BigDecimal("0.1"))).isFalse();
		assertThat(DisplayCurrency.USD.fits(new BigDecimal("12.34"))).isTrue();
		assertThat(DisplayCurrency.EUR.fits(new BigDecimal("12.345"))).isFalse();
	}
}
