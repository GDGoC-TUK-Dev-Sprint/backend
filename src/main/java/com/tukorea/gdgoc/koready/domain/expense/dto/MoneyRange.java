package com.tukorea.gdgoc.koready.domain.expense.dto;

import com.tukorea.gdgoc.koready.global.dto.Money;

/** 금액 범위 (최소~최대) */
public record MoneyRange(Money min, Money max) {
}
