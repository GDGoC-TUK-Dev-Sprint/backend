package com.tukorea.gdgoc.koready.domain.expense.dto;

import com.tukorea.gdgoc.koready.domain.expense.entity.ExpenseItem;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripResponse;
import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.dto.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 예상 경비 (API 명세 25, 04 예상 경비 화면). 금액은 표시 통화로 환산한 범위이고 숙박·항공은 들어 있지 않다.
 * 1인(perPerson)과 인원 합계(total)를 둘 다 주므로 1인/전체 전환(04-03)은 다시 요청하지 않는다.
 *
 * @param guidance 예산 조정 안내 (1인 기준, 템플릿 문장). 희망 예산이 없으면 null
 * @param limitations 데이터 한계 (화면에 그대로 쓰는 문장)
 * @param sources 근거 출처 (통계 기간·환율 고시일)
 * @param exchangeRate 환산에 쓴 환율. KRW면 null
 */
public record CostEstimateResponse(
		TripResponse trip,
		CostBreakdown perPerson,
		CostBreakdown total,
		String guidance,
		List<Limitation> limitations,
		List<Source> sources,
		ExchangeRateInfo exchangeRate) {

	/** 기준(1인 / 인원 합계)별 경비. budget은 희망 예산이 없으면 null */
	public record CostBreakdown(MoneyRange amount, Budget budget, List<ItemCost> items, List<DayCost> days) {
	}

	/** 희망 예산 비교 (04-04). overBy는 초과액(WITHIN_BUDGET이면 null), leftOver는 남는 금액(OVER_BUDGET이면 null) */
	public record Budget(Money amount, BudgetStatus status, PercentRange usagePct, MoneyRange overBy,
			MoneyRange leftOver) {
	}

	/** WITHIN_BUDGET: 최대도 예산 이하 / MAY_EXCEED: 최소는 예산 이하, 최대는 초과 / OVER_BUDGET: 최소도 예산 초과 */
	public enum BudgetStatus { WITHIN_BUDGET, MAY_EXCEED, OVER_BUDGET }

	/** 희망 예산 대비 % (소수 첫째 자리, 비교 막대) */
	public record PercentRange(BigDecimal min, BigDecimal max) {
	}

	/** 항목별 경비 (04-06). sharePct는 범위 가운데값 기준 비율, basis는 계산 근거 문장 */
	public record ItemCost(ExpenseItem item, MoneyRange amount, BigDecimal sharePct, String basis) {
	}

	/** 일자별 경비 (04-07). highest는 가장 많이 쓰는 날(한 날만) */
	public record DayCost(int dayNumber, LocalDate date, MoneyRange amount, List<ItemAmount> items, boolean highest) {
	}

	public record ItemAmount(ExpenseItem item, MoneyRange amount) {
	}

	/** 데이터 한계. message는 화면에 그대로 쓰는 문장 (요청 언어) */
	public record Limitation(String code, String message) {
	}

	/** 근거 출처. detail은 기간·기준일 (없으면 null) */
	public record Source(String name, String detail) {
	}

	/** 환산에 쓴 환율: 표시 통화 1단위당 원화(JPY는 1엔 기준), 고시일 */
	public record ExchangeRateInfo(DisplayCurrency currency, BigDecimal krwPerUnit, LocalDate rateDate) {
	}
}
