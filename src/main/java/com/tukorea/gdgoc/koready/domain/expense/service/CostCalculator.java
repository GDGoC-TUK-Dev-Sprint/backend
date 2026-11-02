package com.tukorea.gdgoc.koready.domain.expense.service;

import com.tukorea.gdgoc.koready.domain.datalab.entity.Industry;
import com.tukorea.gdgoc.koready.domain.expense.config.ExpenseProperties;
import com.tukorea.gdgoc.koready.domain.expense.entity.ExpenseItem;
import com.tukorea.gdgoc.koready.domain.expense.repository.CostStatsRepository;
import com.tukorea.gdgoc.koready.domain.expense.repository.TripScheduleRepository;
import com.tukorea.gdgoc.koready.domain.place.entity.FeeAudience;
import com.tukorea.gdgoc.koready.domain.trip.entity.Trip;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripDay;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * 여행 일정으로 항목별·일자별 1인 예상 경비 범위를 계산한다. 숙박·항공은 경비에 넣지 않는다.
 * <ul>
 *   <li>식비·쇼핑·기타: 방한 외국인 카드 소비 통계(국적별) → 1인 평균 소비 × 업종 비율 ÷ 체류일 가정. 최근 월들의 최소~최대가 범위</li>
 *   <li>교통: 이동 구간(Leg) 운임 합계 (택시 요금은 계산하지 않는다). 최소=최대</li>
 *   <li>관광·입장료: 식사 자리가 아닌 일정 장소의 성인 요금. 같은 장소의 성인 요금이 여럿이면 최소~최대</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
class CostCalculator {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	/**
	 * 카드 통계 업종을 경비 항목으로 묶는 규칙. 숙박(LODGING)은 경비에서 빼고, 교통(TRANSPORT)은 이동 구간 운임으로 계산한다.
	 * 업종 묶음은 팀 확인이 필요하다.
	 */
	private static final Map<ExpenseItem, Set<Industry>> STAT_ITEMS = Map.of(
			ExpenseItem.FOOD, Set.of(Industry.FOOD),
			ExpenseItem.SHOPPING, Set.of(Industry.SHOPPING),
			ExpenseItem.OTHER, Set.of(Industry.LEISURE, Industry.MEDICAL, Industry.TRAVEL));

	private final TripScheduleRepository schedule;
	private final CostStatsRepository stats;
	private final ExpenseProperties properties;

	CostEstimate calculate(Trip trip) {
		List<TripDay> days = schedule.findDays(trip.getId());
		if (days.isEmpty()) {
			throw new ApiException(ErrorCode.ROUTE_NOT_CREATED);
		}
		Map<ExpenseItem, CostEstimate.Range> statPerDay = statPerDay(trip.getNationalityCode());
		Map<Long, Integer> fareByDay = fareByDay(trip.getId());
		Map<Long, CostEstimate.Range> feeByDay = feeByDay(trip.getId());

		List<CostEstimate.Day> result = new ArrayList<>();
		for (TripDay day : days) {
			Map<ExpenseItem, CostEstimate.Range> items = new EnumMap<>(ExpenseItem.class);
			for (ExpenseItem item : STAT_ITEMS.keySet()) {
				items.put(item, statPerDay.getOrDefault(item, CostEstimate.Range.ZERO));
			}
			int fare = fareByDay.getOrDefault(day.getId(), 0);
			items.put(ExpenseItem.TRANSPORT, new CostEstimate.Range(fare, fare));
			items.put(ExpenseItem.SIGHTSEEING, feeByDay.getOrDefault(day.getId(), CostEstimate.Range.ZERO));
			result.add(new CostEstimate.Day(day, items));
		}

		CostEstimate.Source statSource = statPerDay.isEmpty() ? CostEstimate.Source.NO_DATA : CostEstimate.Source.CARD_STATS;
		Map<ExpenseItem, CostEstimate.Source> sources = new EnumMap<>(ExpenseItem.class);
		for (ExpenseItem item : STAT_ITEMS.keySet()) {
			sources.put(item, statSource);
		}
		sources.put(ExpenseItem.TRANSPORT, CostEstimate.Source.ROUTE_FARES);
		sources.put(ExpenseItem.SIGHTSEEING, CostEstimate.Source.PLACE_FEES);
		return new CostEstimate(result, sources);
	}

	/** 식비·쇼핑·기타의 1일 1인 범위. 통계가 없는 국적이면 빈 맵 */
	private Map<ExpenseItem, CostEstimate.Range> statPerDay(String countryCode) {
		List<CostStatsRepository.MonthStat> months =
				stats.findMonthStats(countryCode, PageRequest.of(0, Math.max(1, properties.statMonths())));
		if (months.isEmpty()) {
			return Map.of();
		}
		List<String> yms = months.stream().map(CostStatsRepository.MonthStat::getYm).toList();
		Map<String, Map<Industry, BigDecimal>> sharesByMonth = new HashMap<>();
		for (CostStatsRepository.IndustryShare share : stats.findIndustryShares(countryCode, yms)) {
			sharesByMonth.computeIfAbsent(share.getYm(), ym -> new EnumMap<>(Industry.class))
					.put(share.getIndustry(), share.getSharePct());
		}

		BigDecimal stayDays = BigDecimal.valueOf(Math.max(1, properties.referenceStayDays()));
		Map<ExpenseItem, int[]> bounds = new EnumMap<>(ExpenseItem.class);
		for (CostStatsRepository.MonthStat month : months) {
			Map<Industry, BigDecimal> shares = sharesByMonth.get(month.getYm());
			if (shares == null) {
				continue;
			}
			// 그 달 해당 국적 방문자 1인 평균 카드 소비 = (전체 소비액 × 소비 비율) ÷ (전체 방문자 × 방문 비율)
			BigDecimal numerator = BigDecimal.valueOf(month.getTotalSpendKrw()).multiply(month.getSpendSharePct());
			BigDecimal denominator = BigDecimal.valueOf(month.getTotalVisitors()).multiply(month.getVisitorSharePct());
			if (denominator.signum() <= 0) {
				continue;
			}
			BigDecimal perVisitor = numerator.divide(denominator, MathContext.DECIMAL64);
			for (Map.Entry<ExpenseItem, Set<Industry>> entry : STAT_ITEMS.entrySet()) {
				BigDecimal sharePct = entry.getValue().stream()
						.map(shares::get)
						.filter(java.util.Objects::nonNull)
						.reduce(BigDecimal.ZERO, BigDecimal::add);
				int perDay = perVisitor.multiply(sharePct).divide(HUNDRED, MathContext.DECIMAL64)
						.divide(stayDays, MathContext.DECIMAL64)
						.setScale(0, RoundingMode.HALF_UP).intValue();
				int[] bound = bounds.computeIfAbsent(entry.getKey(), item -> new int[] {perDay, perDay});
				bound[0] = Math.min(bound[0], perDay);
				bound[1] = Math.max(bound[1], perDay);
			}
		}
		return bounds.entrySet().stream().collect(Collectors.toMap(
				Map.Entry::getKey, e -> new CostEstimate.Range(e.getValue()[0], e.getValue()[1]),
				(a, b) -> a, () -> new EnumMap<>(ExpenseItem.class)));
	}

	/** 일자별 이동 운임 합계. 운임을 못 받은 구간은 0으로 본다 */
	private Map<Long, Integer> fareByDay(Long tripId) {
		Map<Long, Integer> sums = new HashMap<>();
		for (TripScheduleRepository.LegFare leg : schedule.findLegFares(tripId)) {
			if (leg.getFareKrw() != null) {
				sums.merge(leg.getDayId(), leg.getFareKrw(), Integer::sum);
			}
		}
		return sums;
	}

	/** 일자별 입장료 범위 (식사 자리는 식비로 보므로 뺀다) */
	private Map<Long, CostEstimate.Range> feeByDay(Long tripId) {
		List<TripScheduleRepository.ItemRef> items = schedule.findItems(tripId).stream()
				.filter(item -> item.getMealType() == null)
				.toList();
		if (items.isEmpty()) {
			return Map.of();
		}
		Set<Long> placeIds = items.stream().map(TripScheduleRepository.ItemRef::getPlaceId).collect(Collectors.toSet());
		Map<Long, CostEstimate.Range> feeByPlace = adultFees(placeIds);
		Map<Long, CostEstimate.Range> byDay = new HashMap<>();
		for (TripScheduleRepository.ItemRef item : items) {
			byDay.merge(item.getDayId(), feeByPlace.getOrDefault(item.getPlaceId(), CostEstimate.Range.ZERO),
					CostEstimate.Range::plus);
		}
		return byDay;
	}

	/** 장소별 성인 요금 범위. 성인 요금 정보가 없는 장소는 포함하지 않는다 (0원으로 본다) */
	private Map<Long, CostEstimate.Range> adultFees(Set<Long> placeIds) {
		Map<Long, List<Integer>> amounts = new HashMap<>();
		for (TripScheduleRepository.FeeRow fee : schedule.findFees(placeIds)) {
			if (fee.getAudience() == FeeAudience.ADULT) {
				amounts.computeIfAbsent(fee.getPlaceId(), id -> new ArrayList<>()).add(fee.getAmountKrw());
			}
		}
		Map<Long, CostEstimate.Range> result = new HashMap<>();
		amounts.forEach((placeId, list) ->
				result.put(placeId, new CostEstimate.Range(Collections.min(list), Collections.max(list))));
		return result;
	}
}
