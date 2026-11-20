package com.tukorea.gdgoc.koready.domain.expense.service;

import com.tukorea.gdgoc.koready.domain.expense.dto.MoneyRange;
import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.dto.Money;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 원화 금액을 표시 통화로 바꾼다 (API 명세 공통 규칙 "통화").
 * <ul>
 *   <li>금액 하나: KRW·JPY는 정수, USD·EUR는 소수 둘째 자리로 반올림</li>
 *   <li>범위: min은 내림·max는 올림해 KRW 1,000원·JPY 100엔·USD·EUR 1 단위로 맞춘다 (실제 범위를 벗어나지 않게)</li>
 * </ul>
 */
final class MoneyConverter {

	private final DisplayCurrency currency;
	private final BigDecimal krwPerUnit;

	private MoneyConverter(DisplayCurrency currency, BigDecimal krwPerUnit) {
		this.currency = currency;
		this.krwPerUnit = krwPerUnit;
	}

	static MoneyConverter krw() {
		return new MoneyConverter(DisplayCurrency.KRW, BigDecimal.ONE);
	}

	/** @param krwPerUnit 표시 통화 1단위당 원화 (JPY는 1엔 기준) */
	static MoneyConverter of(DisplayCurrency currency, BigDecimal krwPerUnit) {
		return new MoneyConverter(currency, krwPerUnit);
	}

	MoneyRange range(CostEstimate.Range range) {
		return new MoneyRange(
				roundToRangeUnit(toDisplay(range.min()), RoundingMode.FLOOR),
				roundToRangeUnit(toDisplay(range.max()), RoundingMode.CEILING),
				currency);
	}

	/** 단일 금액 (희망 예산 등). 반올림 */
	Money amount(long krw) {
		if (currency == DisplayCurrency.KRW) {
			return Money.krw(krw);
		}
		return new Money(toDisplay(krw).setScale(currency.fractionDigits(), RoundingMode.HALF_UP), currency);
	}

	/** 원화를 표시 통화로 (반올림 전) */
	private BigDecimal toDisplay(long krw) {
		BigDecimal value = BigDecimal.valueOf(krw);
		return currency == DisplayCurrency.KRW ? value : value.divide(krwPerUnit, MathContext.DECIMAL64);
	}

	/** 범위를 맞추는 단위 배수로 내림·올림 */
	private BigDecimal roundToRangeUnit(BigDecimal value, RoundingMode mode) {
		BigDecimal unit = BigDecimal.valueOf(switch (currency) {
			case KRW -> 1_000;
			case JPY -> 100;
			case USD, EUR -> 1;
		});
		return value.divide(unit, 0, mode).multiply(unit);
	}
}
