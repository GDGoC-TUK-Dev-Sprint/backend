package com.tukorea.gdgoc.koready.domain.expense.service;

import com.tukorea.gdgoc.koready.domain.exchange.entity.Currency;
import com.tukorea.gdgoc.koready.domain.exchange.entity.ExchangeRate;
import com.tukorea.gdgoc.koready.domain.expense.config.ExpenseProperties;
import com.tukorea.gdgoc.koready.domain.expense.dto.CostEstimateResponse;
import com.tukorea.gdgoc.koready.domain.expense.dto.MoneyRange;
import com.tukorea.gdgoc.koready.domain.expense.entity.ExpenseItem;
import com.tukorea.gdgoc.koready.domain.expense.entity.TripCostEstimate;
import com.tukorea.gdgoc.koready.domain.expense.repository.ExchangeRateLookupRepository;
import com.tukorea.gdgoc.koready.domain.expense.repository.TripCostEstimateRepository;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripResponse;
import com.tukorea.gdgoc.koready.domain.trip.entity.Trip;
import com.tukorea.gdgoc.koready.domain.trip.service.TripService;
import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import com.tukorea.gdgoc.koready.global.web.Language;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예상 경비 화면과 여행 확정. 여행은 {@link TripService}로 찾고 바꾼다.
 * 경비는 원화로 계산하고, 화면 응답만 표시 통화로 환산한다.
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

	/** 화면에 보여 주는 항목 순서 */
	private static final List<ExpenseItem> DISPLAY_ORDER = List.of(
			ExpenseItem.FOOD, ExpenseItem.SIGHTSEEING, ExpenseItem.TRANSPORT, ExpenseItem.SHOPPING, ExpenseItem.OTHER);

	private final TripService tripService;
	private final CostCalculator calculator;
	private final TripCostEstimateRepository costEstimates;
	private final ExchangeRateLookupRepository exchangeRates;
	private final ExpenseProperties properties;
	private final Clock clock;

	/** 예상 경비 화면. 동선을 만들기 전이면 {@code 409 ROUTE_NOT_CREATED} */
	@Transactional(readOnly = true)
	public CostEstimateResponse getEstimate(Long userId, Long tripId, Language language, DisplayCurrency currency) {
		Trip trip = tripService.findMyTrip(userId, tripId);
		CostEstimate estimate = calculator.calculate(trip);
		return toResponse(trip, estimate, language, converter(currency));
	}

	/**
	 * 이 예산으로 여행 확정하기. 여행을 확정하고(확정한 여행은 편집할 수 없다) 항목별 1인 예상 경비 범위(원화)를 저장한다.
	 * 계산에 실패하면 확정도 취소된다.
	 */
	@Transactional
	public TripResponse confirm(Long userId, Long tripId, Language language) {
		Trip trip = tripService.confirm(userId, tripId);
		CostEstimate estimate = calculator.calculate(trip);
		for (ExpenseItem item : ExpenseItem.values()) {
			CostEstimate.Range range = estimate.item(item);
			costEstimates.save(TripCostEstimate.of(trip, item, range.min(), range.max()));
		}
		return tripService.toResponse(trip, language);
	}

	/** 표시 통화로 바꾸는 도구. 환율은 오늘(서울) 이전 가장 최근 고시 환율. 환율이 없으면 500 */
	private MoneyConverter converter(DisplayCurrency currency) {
		if (currency == DisplayCurrency.KRW) {
			return MoneyConverter.krw();
		}
		Currency rateCurrency = Currency.valueOf(currency.name());
		ExchangeRate rate = exchangeRates.findLatest(rateCurrency, LocalDate.now(clock), PageRequest.of(0, 1))
				.stream().findFirst()
				.orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
						currency + " 환율 데이터가 없어 환산할 수 없습니다"));
		return MoneyConverter.of(currency, rate.getKrwPerUnit());
	}

	private CostEstimateResponse toResponse(Trip trip, CostEstimate estimate, Language language,
			MoneyConverter converter) {
		int party = trip.getPartySize();
		CostEstimate.Range perPerson = estimate.total();
		long totalMid = perPerson.mid();

		List<CostEstimateResponse.ItemCost> items = new ArrayList<>();
		for (ExpenseItem item : DISPLAY_ORDER) {
			CostEstimate.Range range = estimate.item(item);
			items.add(new CostEstimateResponse.ItemCost(
					ExpenseLabels.item(item, language),
					converter.range(range),
					converter.range(range.times(party)),
					sharePct(range.mid(), totalMid),
					ExpenseLabels.source(estimate.sources().get(item), properties.referenceStayDays(), language)));
		}

		long peakMid = estimate.days().stream().mapToLong(day -> day.total().mid()).max().orElse(0);
		boolean peakAssigned = false;
		List<CostEstimateResponse.DayCost> days = new ArrayList<>();
		for (CostEstimate.Day day : estimate.days()) {
			CostEstimate.Range dayTotal = day.total();
			boolean peak = !peakAssigned && peakMid > 0 && dayTotal.mid() == peakMid;
			peakAssigned |= peak;
			List<CostEstimateResponse.ItemAmount> amounts = new ArrayList<>();
			for (ExpenseItem item : DISPLAY_ORDER) {
				CostEstimate.Range range = day.items().getOrDefault(item, CostEstimate.Range.ZERO);
				amounts.add(new CostEstimateResponse.ItemAmount(
						item, converter.range(range), converter.range(range.times(party))));
			}
			days.add(new CostEstimateResponse.DayCost(day.tripDay().getDayNumber(), day.tripDay().getTripDate(),
					converter.range(dayTotal), converter.range(dayTotal.times(party)), peak, amounts));
		}

		return new CostEstimateResponse(
				tripService.toResponse(trip, language),
				converter.range(perPerson),
				converter.range(perPerson.times(party)),
				budget(trip, perPerson, party, converter),
				items,
				days);
	}

	private static CostEstimateResponse.Budget budget(Trip trip, CostEstimate.Range perPerson, int party,
			MoneyConverter converter) {
		Integer budget = trip.getBudgetPerPersonKrw();
		if (budget == null) {
			return new CostEstimateResponse.Budget(null, null, CostEstimateResponse.BudgetStatus.NO_BUDGET, null, null);
		}
		CostEstimateResponse.BudgetStatus status;
		if (perPerson.max() <= budget) {
			status = CostEstimateResponse.BudgetStatus.WITHIN;
		} else if (perPerson.min() > budget) {
			status = CostEstimateResponse.BudgetStatus.OVER;
		} else {
			status = CostEstimateResponse.BudgetStatus.MAY_EXCEED;
		}
		MoneyRange overPerPerson = null;
		MoneyRange overTotal = null;
		if (status != CostEstimateResponse.BudgetStatus.WITHIN) {
			CostEstimate.Range over = new CostEstimate.Range(
					Math.max(0, perPerson.min() - budget), Math.max(0, perPerson.max() - budget));
			overPerPerson = converter.range(over);
			overTotal = converter.range(over.times(party));
		}
		long budgetTotal = (long) budget * party;
		return new CostEstimateResponse.Budget(converter.amount(budget), converter.amount(budgetTotal),
				status, overPerPerson, overTotal);
	}

	/** 전체 경비 대비 비중(%, 소수 첫째 자리). 중간값 기준 */
	private static BigDecimal sharePct(long itemMid, long totalMid) {
		if (totalMid <= 0) {
			return BigDecimal.ZERO.setScale(1);
		}
		return BigDecimal.valueOf(itemMid * 100L).divide(BigDecimal.valueOf(totalMid), 1, RoundingMode.HALF_UP);
	}
}
