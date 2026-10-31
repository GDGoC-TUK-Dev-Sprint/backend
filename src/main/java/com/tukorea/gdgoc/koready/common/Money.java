package com.tukorea.gdgoc.koready.common;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * 금액 하나. 요청 금액(희망 예산·실제 지출)은 이 형식으로 받아 원화로 바꿔 저장하고, 응답 금액은 표시 통화로 환산한 값이다.
 */
public record Money(@NotNull @PositiveOrZero BigDecimal amount, @NotNull DisplayCurrency currency) {

	public static Money krw(long amount) {
		return new Money(BigDecimal.valueOf(amount), DisplayCurrency.KRW);
	}
}
