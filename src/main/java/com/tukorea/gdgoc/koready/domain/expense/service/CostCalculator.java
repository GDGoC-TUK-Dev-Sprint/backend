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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
 * 데이터 한계(요금 정보 없는 장소 수, 운임을 받지 못한 구간 수)와 통계 기간도 함께 돌려준다.
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
		Set<String> statMonths = new TreeSet<>();
		Map<ExpenseItem, CostEstimate.Range> statPerDay = statPerDay(trip.getNationalityCode(), statMonths);
		List<TripScheduleRepository.LegFare> legs = schedule.findLegFares(trip.getId());
		Map<Long, Integer> fareByDay = fareByDay(legs);
		int fareUnavailableLegs = (int) legs.stream().filter(leg -> leg.getFareKrw() == null).count();
		Fees fees = feeByDay(trip.getId());

		List<CostEstimate.Day> result = new ArrayList<>();
		for (TripDay day : days) {
			Map<ExpenseItem, CostEstimate.Range> items = new EnumMap<>(ExpenseItem.class);
			for (ExpenseItem item : STAT_ITEMS.keySet()) {
				items.put(item, statPerDay.getOrDefault(item, CostEstimate.Range.ZERO));
			}
			int fare = fareByDay.getOrDefault(day.getId(), 0);
			items.put(ExpenseItem.TRANSPORT, new CostEstimate.Range(fare, fare));
			items.put(ExpenseItem.SIGHTSEEING, fees.byDay().getOrDefault(day.getId(), CostEstimate.Range.ZERO));
			result.add(new CostEstimate.Day(day, items));
		}

		CostEstimate.Source statSource = statPerDay.isEmpty() ? CostEstimate.Source.NO_DATA : CostEstimate.Source.CARD_STATS;
		Map<ExpenseItem, CostEstimate.Source> sources = new EnumMap<>(ExpenseItem.class);
		for (ExpenseItem item : STAT_ITEMS.keySet()) {
			sources.put(item, statSource);
		}
		sources.put(ExpenseItem.TRANSPORT, CostEstimate.Source.ROUTE_FARES);
		sources.put(ExpenseItem.SIGHTSEEING, CostEstimate.Source.PLACE_FEES);
		return new CostEstimate(result, sources, List.copyOf(statMonths), fees.unknownPlaces(), fareUnavailableLegs);
	}

	/** 식비·쇼핑·기타의 1일 1인 범위. 통계가 없는 국적이면 빈 맵. 계산에 쓴 달을 usedMonths에 담는다 */
	private Map<ExpenseItem, CostEstimate.Range> statPerDay(String countryCode, Set<String> usedMonths) {
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
			usedMonths.add(month.getYm());
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
	private static Map<Long, Integer> fareByDay(List<TripScheduleRepository.LegFare> legs) {
		Map<Long, Integer> sums = new HashMap<>();
		for (TripScheduleRepository.LegFare leg : legs) {
			if (leg.getFareKrw() != null) {
				sums.merge(leg.getDayId(), leg.getFareKrw(), Integer::sum);
			}
		}
		return sums;
	}

	/** 일자별 입장료 범위와 요금 정보가 없는 장소 수 */
	private record Fees(Map<Long, CostEstimate.Range> byDay, int unknownPlaces) {
	}

	/** 일자별 입장료 범위 (식사 자리는 식비로 보므로 뺀다) */
	private Fees feeByDay(Long tripId) {
		List<TripScheduleRepository.ItemRef> items = schedule.findItems(tripId).stream()
				.filter(item -> item.getMealType() == null)
				.toList();
		if (items.isEmpty()) {
			return new Fees(Map.of(), 0);
		}
		Set<Long> placeIds = items.stream().map(TripScheduleRepository.ItemRef::getPlaceId).collect(Collectors.toSet());
		Set<Long> placesWithFeeInfo = new HashSet<>();
		Map<Long, List<Integer>> adultAmounts = new HashMap<>();
		for (TripScheduleRepository.FeeRow fee : schedule.findFees(placeIds)) {
			placesWithFeeInfo.add(fee.getPlaceId());
			if (fee.getAudience() == FeeAudience.ADULT) {
				adultAmounts.computeIfAbsent(fee.getPlaceId(), id -> new ArrayList<>()).add(fee.getAmountKrw());
			}
		}
		// 성인 요금 범위. 성인 요금 정보가 없는 장소는 0원으로 본다
		Map<Long, CostEstimate.Range> feeByPlace = new HashMap<>();
		adultAmounts.forEach((placeId, list) ->
				feeByPlace.put(placeId, new CostEstimate.Range(Collections.min(list), Collections.max(list))));

		Map<Long, CostEstimate.Range> byDay = new HashMap<>();
		for (TripScheduleRepository.ItemRef item : items) {
			byDay.merge(item.getDayId(), feeByPlace.getOrDefault(item.getPlaceId(), CostEstimate.Range.ZERO),
					CostEstimate.Range::plus);
		}
		int unknownPlaces = (int) placeIds.stream().filter(id -> !placesWithFeeInfo.contains(id)).count();
		return new Fees(byDay, unknownPlaces);
	}
}
