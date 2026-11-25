package com.tukorea.gdgoc.koready.domain.expense.dto;

import com.tukorea.gdgoc.koready.domain.expense.entity.ExpenseItem;
import com.tukorea.gdgoc.koready.global.dto.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 실제 지출 저장 요청 본문 (API 명세 29, 08-04·06). 항목 수·중복·기타 항목 이름·금액 소수 자리는 서비스가 검사한다.
 *
 * @param basis 입력한 기준 (08-02)
 * @param items 입력한 항목만 보낸다 (입력하지 않은 항목은 0원과 다르다). 저장 순서는 보낸 순서다
 */
public record ExpenseSaveRequest(@NotNull Basis basis, @NotNull List<@NotNull @Valid Item> items) {

	/** PER_PERSON: 1인 금액 / TOTAL: 인원 합계 금액 */
	public enum Basis { PER_PERSON, TOTAL }

	/** label은 기타(OTHER)일 때만 필요하다 (최대 50자) */
	public record Item(@NotNull ExpenseItem item, String label, @NotNull @Valid Money amount) {
	}
}
