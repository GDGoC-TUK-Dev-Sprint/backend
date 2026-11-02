package com.tukorea.gdgoc.koready.domain.expense.controller;

import com.tukorea.gdgoc.koready.domain.expense.dto.CostEstimateResponse;
import com.tukorea.gdgoc.koready.domain.expense.service.ExpenseService;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripResponse;
import com.tukorea.gdgoc.koready.global.auth.LoginUserId;
import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.web.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class ExpenseController {

	private final ExpenseService expenseService;

	/**
	 * 04-01~07 예상 경비 화면. 금액은 {@code currency}(KRW·JPY·USD·EUR, 기본 KRW)로 환산해서 준다.
	 * 경로·currency 파라미터는 명세에 없어 임의로 정함 → 프론트와 합의 필요
	 */
	@GetMapping("/api/trips/{tripId}/cost-estimate")
	CostEstimateResponse costEstimate(@LoginUserId Long userId, @PathVariable Long tripId, Language language,
			@RequestParam(defaultValue = "KRW") DisplayCurrency currency) {
		return expenseService.getEstimate(userId, tripId, language, currency);
	}

	/** 04-08 이 예산으로 여행 확정하기 → 01 메인. 확정한 여행은 편집할 수 없다 */
	@PostMapping("/api/trips/{tripId}/confirm")
	TripResponse confirm(@LoginUserId Long userId, @PathVariable Long tripId, Language language) {
		return expenseService.confirm(userId, tripId, language);
	}
}
