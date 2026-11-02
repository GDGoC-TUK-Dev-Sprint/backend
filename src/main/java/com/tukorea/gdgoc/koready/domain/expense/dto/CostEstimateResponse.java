package com.tukorea.gdgoc.koready.domain.expense.dto;

import com.tukorea.gdgoc.koready.domain.expense.entity.ExpenseItem;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripResponse;
import com.tukorea.gdgoc.koready.global.dto.Code;
import com.tukorea.gdgoc.koready.global.dto.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 예상 경비 (04 예상 경비 화면). 금액은 모두 범위(최소~최대)이고 원화다. 숙박·항공은 들어 있지 않다.
 *
 * @param trip      여행 요약 (상태 배지·기간·인원)
 * @param perPerson 1인 총 예상 경비
 * @param total     전체 인원 총 예상 경비
 */
public record CostEstimateResponse(
		TripResponse trip,
		MoneyRange perPerson,
		MoneyRange total,
		Budget budget,
		List<ItemCost> items,
		List<DayCost> days) {

	/** 희망 예산과 비교. 희망 예산이 없으면 status만 NO_BUDGET이고 나머지는 null이다 */
	public record Budget(
			Money perPerson,
			Money total,
			BudgetStatus status,
			MoneyRange overPerPerson,
			MoneyRange overTotal) {
	}

	/** WITHIN: 예산 안, OVER: 예산 초과, MAY_EXCEED: 예상 범위가 예산을 걸침 */
	public enum BudgetStatus { NO_BUDGET, WITHIN, OVER, MAY_EXCEED }

	/** 항목별 경비. sharePct는 전체 대비 비중(중간값 기준), source는 근거 데이터 */
	public record ItemCost(
			Code item,
			MoneyRange perPerson,
			MoneyRange total,
			BigDecimal sharePct,
			Code source) {
	}

	/** 일자별 경비. peak는 가장 많이 쓰는 날(한 날만) */
	public record DayCost(
			int day,
			LocalDate date,
			MoneyRange perPerson,
			MoneyRange total,
			boolean peak,
			List<ItemAmount> items) {
	}

	public record ItemAmount(ExpenseItem item, MoneyRange perPerson, MoneyRange total) {
	}
}
