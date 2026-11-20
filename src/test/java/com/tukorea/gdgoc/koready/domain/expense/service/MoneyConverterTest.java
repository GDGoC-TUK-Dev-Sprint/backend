package com.tukorea.gdgoc.koready.domain.expense.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.domain.expense.dto.MoneyRange;
import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.dto.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** API 명세 공통 규칙 "통화"의 반올림 (범위는 KRW 1,000원·JPY 100엔·USD·EUR 1 단위) */
class MoneyConverterTest {

	private static final BigDecimal KRW_PER_YEN = new BigDecimal("9.0512");
	private static final BigDecimal KRW_PER_DOLLAR = new BigDecimal("1398.5");

	@Test
	void krwRangeRoundsTo1000Won() {
		MoneyRange range = MoneyConverter.krw().range(new CostEstimate.Range(12_345, 67_001));

		assertThat(range.min()).isEqualByComparingTo("12000");
		assertThat(range.max()).isEqualByComparingTo("68000");
		assertThat(range.currency()).isEqualTo(DisplayCurrency.KRW);
	}

	@Test
	void jpyRangeRoundsTo100Yen() {
		// 10,000원 = 1,104.8엔 → 1,100 / 20,000원 = 2,209.7엔 → 2,300
		MoneyRange range = MoneyConverter.of(DisplayCurrency.JPY, KRW_PER_YEN).range(new CostEstimate.Range(10_000, 20_000));

		assertThat(range.min()).isEqualByComparingTo("1100");
		assertThat(range.max()).isEqualByComparingTo("2300");
	}

	@Test
	void usdRangeRoundsTo1Dollar() {
		// 10,000원 = 7.15달러 → 7 / 20,000원 = 14.30달러 → 15
		MoneyRange range = MoneyConverter.of(DisplayCurrency.USD, KRW_PER_DOLLAR).range(new CostEstimate.Range(10_000, 20_000));

		assertThat(range.min()).isEqualByComparingTo("7");
		assertThat(range.max()).isEqualByComparingTo("15");
		assertThat(range.currency()).isEqualTo(DisplayCurrency.USD);
	}

	@Test
	void zeroRangeStaysZero() {
		MoneyRange range = MoneyConverter.of(DisplayCurrency.EUR, KRW_PER_DOLLAR).range(CostEstimate.Range.ZERO);

		assertThat(range.min()).isEqualByComparingTo("0");
		assertThat(range.max()).isEqualByComparingTo("0");
	}

	@Test
	void singleAmountRoundsToCurrencyDecimals() {
		Money usd = MoneyConverter.of(DisplayCurrency.USD, KRW_PER_DOLLAR).amount(1_000_000);
		Money krw = MoneyConverter.krw().amount(12_345);

		assertThat(usd.amount()).isEqualByComparingTo("715.05");
		assertThat(krw.amount()).isEqualByComparingTo("12345");
	}
}
