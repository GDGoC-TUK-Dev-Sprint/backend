package com.tukorea.gdgoc.koready.domain.expense.service;

import com.tukorea.gdgoc.koready.domain.expense.dto.MoneyRange;
import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.dto.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 원화 금액을 표시 통화로 바꾼다. 범위는 최소를 내림, 최대를 올림해서 실제 범위를 벗어나지 않게 한다.
 * 소수 자리는 원·엔 0자리, 달러·유로 2자리.
 */
final class MoneyConverter {

	private final DisplayCurrency currency;
	private final BigDecimal krwPerUnit;
	private final int scale;

	private MoneyConverter(DisplayCurrency currency, BigDecimal krwPerUnit) {
		this.currency = currency;
		this.krwPerUnit = krwPerUnit;
		this.scale = (currency == DisplayCurrency.USD || currency == DisplayCurrency.EUR) ? 2 : 0;
	}

	static MoneyConverter krw() {
		return new MoneyConverter(DisplayCurrency.KRW, BigDecimal.ONE);
	}

	/** @param krwPerUnit 표시 통화 1단위당 원화 (JPY는 1엔 기준) */
	static MoneyConverter of(DisplayCurrency currency, BigDecimal krwPerUnit) {
		return new MoneyConverter(currency, krwPerUnit);
	}

	MoneyRange range(CostEstimate.Range range) {
		return new MoneyRange(convert(range.min(), RoundingMode.DOWN), convert(range.max(), RoundingMode.UP));
	}

	/** 단일 금액 (희망 예산 등). 반올림 */
	Money amount(long krw) {
		return convert(krw, RoundingMode.HALF_UP);
	}

	private Money convert(long krw, RoundingMode mode) {
		if (currency == DisplayCurrency.KRW) {
			return Money.krw(krw);
		}
		return new Money(BigDecimal.valueOf(krw).divide(krwPerUnit, scale, mode), currency);
	}
}
