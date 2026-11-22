package com.tukorea.gdgoc.koready.domain.expense.dto;

import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import java.math.BigDecimal;

/** 금액 범위 (API 명세 공통 객체 MoneyRange: min, max, currency). 표시 통화로 환산한 값이다 */
public record MoneyRange(BigDecimal min, BigDecimal max, DisplayCurrency currency) {
}
