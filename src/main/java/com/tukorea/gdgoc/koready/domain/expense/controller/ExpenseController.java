package com.tukorea.gdgoc.koready.domain.expense.controller;

import com.tukorea.gdgoc.koready.domain.expense.dto.CostEstimateResponse;
import com.tukorea.gdgoc.koready.domain.expense.dto.ExpenseReportResponse;
import com.tukorea.gdgoc.koready.domain.expense.dto.ExpenseSaveRequest;
import com.tukorea.gdgoc.koready.domain.expense.dto.ExpenseSheetResponse;
import com.tukorea.gdgoc.koready.domain.expense.service.ExpenseService;
import com.tukorea.gdgoc.koready.domain.expense.service.TripExpenseService;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripResponse;
import com.tukorea.gdgoc.koready.global.auth.LoginUserId;
import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.web.Language;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class ExpenseController {

	private final ExpenseService expenseService;
	private final TripExpenseService tripExpenseService;

	/**
	 * 04-01~07 예상 경비 화면 (API 명세 25). 금액은 헤더 {@code X-Currency}(KRW·JPY·USD·EUR, 없으면 KRW)로 환산해서 준다.
	 */
	@GetMapping("/api/trips/{tripId}/cost-estimate")
	CostEstimateResponse costEstimate(@LoginUserId Long userId, @PathVariable Long tripId, Language language,
			DisplayCurrency currency) {
		return expenseService.getEstimate(userId, tripId, language, currency);
	}

	/** 04-08 이 예산으로 여행 확정하기 → 01 메인. 확정한 여행은 편집할 수 없다 */
	@PostMapping("/api/trips/{tripId}/confirm")
	TripResponse confirm(@LoginUserId Long userId, @PathVariable Long tripId, Language language,
			DisplayCurrency currency) {
		return expenseService.confirm(userId, tripId, language, currency);
	}

	/** 08-01·03 지출 입력 화면 (API 명세 28). 끝난 확정 여행만 받는다 */
	@GetMapping("/api/trips/{tripId}/expenses")
	ExpenseSheetResponse expenses(@LoginUserId Long userId, @PathVariable Long tripId, Language language,
			DisplayCurrency currency) {
		return tripExpenseService.getSheet(userId, tripId, language, currency);
	}

	/** 08-04·06 실제 지출 저장 → 09 리포트 (API 명세 29). 그 여행의 지출을 통째로 덮어쓴다 */
	@PutMapping("/api/trips/{tripId}/expenses")
	ExpenseSheetResponse saveExpenses(@LoginUserId Long userId, @PathVariable Long tripId,
			@Valid @RequestBody ExpenseSaveRequest request, Language language, DisplayCurrency currency) {
		return tripExpenseService.save(userId, tripId, request, language, currency);
	}

	/** 09-01~07 지출 리포트 (API 명세 30) */
	@GetMapping("/api/trips/{tripId}/expense-report")
	ExpenseReportResponse expenseReport(@LoginUserId Long userId, @PathVariable Long tripId, Language language,
			DisplayCurrency currency) {
		return tripExpenseService.getReport(userId, tripId, language, currency);
	}
}
