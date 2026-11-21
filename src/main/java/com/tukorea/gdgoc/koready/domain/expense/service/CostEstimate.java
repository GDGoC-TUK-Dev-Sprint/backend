package com.tukorea.gdgoc.koready.domain.expense.service;

import com.tukorea.gdgoc.koready.domain.expense.entity.ExpenseItem;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripDay;
import java.util.List;
import java.util.Map;

/**
 * 계산한 예상 경비 (원화, 1인 기준). 일자별 항목별 범위를 갖고 있고 합계는 여기서 더한다.
 *
 * @param statMonths 카드 소비 통계에서 쓴 달 (오름차순, 근거 출처의 기간). 통계가 없으면 빈 목록
 * @param feeUnknownPlaces 요금 정보가 하나도 없는 일정 장소 수 (데이터 한계 FEE_UNKNOWN)
 * @param fareUnavailableLegs 운임을 받지 못한 이동 구간 수 (데이터 한계 FARE_UNAVAILABLE)
 */
record CostEstimate(List<Day> days, Map<ExpenseItem, Source> sources, List<String> statMonths,
		int feeUnknownPlaces, int fareUnavailableLegs) {

	/** 최소~최대 (원) */
	record Range(int min, int max) {

		static final Range ZERO = new Range(0, 0);

		Range plus(Range other) {
			return new Range(min + other.min, max + other.max);
		}

		Range times(int count) {
			return new Range(min * count, max * count);
		}

		/** 중간값 (비중·최대 지출일 계산용) */
		long mid() {
			return ((long) min + max) / 2;
		}
	}

	/** 항목 금액의 근거 데이터 */
	enum Source { CARD_STATS, ROUTE_FARES, PLACE_FEES, NO_DATA }

	record Day(TripDay tripDay, Map<ExpenseItem, Range> items) {

		Range total() {
			return items.values().stream().reduce(Range.ZERO, Range::plus);
		}
	}

	Range item(ExpenseItem item) {
		return days.stream().map(day -> day.items().getOrDefault(item, Range.ZERO)).reduce(Range.ZERO, Range::plus);
	}

	Range total() {
		return days.stream().map(Day::total).reduce(Range.ZERO, Range::plus);
	}
}
