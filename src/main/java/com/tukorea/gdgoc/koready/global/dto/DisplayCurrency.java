package com.tukorea.gdgoc.koready.global.dto;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * 표시 통화. 금액은 원화로 저장·계산하고 응답할 때 이 통화로 환산한다.
 * 요청 통화는 헤더 {@code X-Currency}로 받고(없으면 KRW), 컨트롤러 파라미터 {@code DisplayCurrency currency}로 받는다.
 */
public enum DisplayCurrency {

	KRW(0), JPY(0), USD(2), EUR(2);

	/** 표시 통화를 싣는 요청 헤더 */
	public static final String HEADER = "X-Currency";

	/** 소수 자리 수 (원·엔 0, 달러·유로 2) */
	private final int fractionDigits;

	DisplayCurrency(int fractionDigits) {
		this.fractionDigits = fractionDigits;
	}

	public int fractionDigits() {
		return fractionDigits;
	}

	/** 금액이 이 통화의 소수 자리 안인지 (원·엔은 정수, 달러·유로는 소수 둘째 자리까지) */
	public boolean fits(BigDecimal amount) {
		return amount.stripTrailingZeros().scale() <= fractionDigits;
	}

	/**
	 * 헤더 값. 없거나 비었으면 KRW, 대소문자는 가리지 않는다.
	 *
	 * @throws IllegalArgumentException 지원하지 않는 통화
	 */
	public static DisplayCurrency fromHeader(String value) {
		if (value == null || value.isBlank()) {
			return KRW;
		}
		return valueOf(value.trim().toUpperCase(Locale.ROOT));
	}
}
