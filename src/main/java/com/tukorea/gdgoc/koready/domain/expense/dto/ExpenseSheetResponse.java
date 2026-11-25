package com.tukorea.gdgoc.koready.domain.expense.dto;

import com.tukorea.gdgoc.koready.domain.expense.entity.ExpenseItem;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripResponse;
import com.tukorea.gdgoc.koready.global.dto.Money;
import java.util.List;

/**
 * 지출 입력 화면 (API 명세 28 ExpenseSheet, 08-01·03). 29 지출 저장도 이 응답을 준다.
 * 금액은 표시 통화로 환산한 값이고, 1인(perPerson)과 인원 합계(total)를 둘 다 주므로 08-02 기준 전환은 다시 요청하지 않는다.
 *
 * @param estimates     확정할 때 저장한 항목별 예상 경비 (08-03 사전 예상)
 * @param estimateTotal 예상 경비 합계 (08-05)
 * @param budget        희망 예산 (08-05 소진율). 희망 예산이 없으면 null
 * @param expenses      저장한 실제 지출 (보낸 순서). 저장하지 않았으면 빈 목록
 * @param saved         실제 지출을 저장했는지
 */
public record ExpenseSheetResponse(
		TripResponse trip,
		List<ItemEstimate> estimates,
		EstimateTotal estimateTotal,
		Budget budget,
		List<ItemExpense> expenses,
		boolean saved) {

	public record ItemEstimate(ExpenseItem item, MoneyRange perPerson, MoneyRange total) {
	}

	public record EstimateTotal(MoneyRange perPerson, MoneyRange total) {
	}

	public record Budget(Money perPerson, Money total) {
	}

	/** label은 기타(OTHER) 항목 이름 (다른 항목은 null) */
	public record ItemExpense(ExpenseItem item, String label, Money perPerson, Money total) {
	}
}
