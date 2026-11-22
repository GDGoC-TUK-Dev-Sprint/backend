package com.tukorea.gdgoc.koready.domain.expense.service;

import com.tukorea.gdgoc.koready.domain.exchange.service.ExchangeService;
import com.tukorea.gdgoc.koready.domain.expense.config.ExpenseProperties;
import com.tukorea.gdgoc.koready.domain.expense.dto.CostEstimateResponse;
import com.tukorea.gdgoc.koready.domain.expense.dto.CostEstimateResponse.BudgetStatus;
import com.tukorea.gdgoc.koready.domain.expense.dto.MoneyRange;
import com.tukorea.gdgoc.koready.domain.expense.entity.ExpenseItem;
import com.tukorea.gdgoc.koready.domain.expense.entity.TripCostEstimate;
import com.tukorea.gdgoc.koready.domain.expense.repository.TripCostEstimateRepository;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripResponse;
import com.tukorea.gdgoc.koready.domain.trip.entity.Trip;
import com.tukorea.gdgoc.koready.domain.trip.service.TripService;
import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.web.Language;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예상 경비 화면(API 명세 25)과 여행 확정(26). 여행은 {@link TripService}로 찾고 바꾼다.
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
	private final ExchangeService exchangeService;
	private final ExpenseProperties properties;

	/**
	 * 예상 경비 화면. 동선을 만들기 전이면 {@code 409 ROUTE_NOT_CREATED}, 외화 환율이 없으면 {@code 503 EXCHANGE_RATE_UNAVAILABLE}
	 */
	@Transactional(readOnly = true)
	public CostEstimateResponse getEstimate(Long userId, Long tripId, Language language, DisplayCurrency currency) {
		Trip trip = tripService.findMyTrip(userId, tripId);
		CostEstimate estimate = calculator.calculate(trip);
		ExchangeService.Rate rate = currency == DisplayCurrency.KRW ? null : exchangeService.rate(currency);
		MoneyConverter converter = rate == null ? MoneyConverter.krw() : MoneyConverter.of(currency, rate.krwPerUnit());
		return toResponse(trip, estimate, language, converter, rate);
	}

	/**
	 * 이 예산으로 여행 확정하기 (API 명세 26). 여행을 확정하고(확정한 여행은 편집할 수 없다) 항목별 1인 예상 경비 범위(원화)를 저장한다.
	 * 이미 확정한 여행이면 그대로 200이고 예상 경비도 다시 저장하지 않는다. 계산에 실패하면 확정도 취소된다.
	 */
	@Transactional
	public TripResponse confirm(Long userId, Long tripId, Language language, DisplayCurrency currency) {
		boolean confirmedNow = tripService.confirm(userId, tripId);
		Trip trip = tripService.findMyTrip(userId, tripId);
		if (confirmedNow) {
			CostEstimate estimate = calculator.calculate(trip);
			for (ExpenseItem item : ExpenseItem.values()) {
				CostEstimate.Range range = estimate.item(item);
				costEstimates.save(TripCostEstimate.of(trip, item, range.min(), range.max()));
			}
		}
		return tripService.toResponse(trip, language, currency);
	}

	private CostEstimateResponse toResponse(Trip trip, CostEstimate estimate, Language language,
			MoneyConverter converter, ExchangeService.Rate rate) {
		int party = trip.getPartySize();
		Integer budget = trip.getBudgetPerPersonKrw();
		CostEstimateResponse.CostBreakdown perPerson = breakdown(estimate, 1,
				budget == null ? null : budget.longValue(), language, converter);
		CostEstimateResponse.CostBreakdown total = breakdown(estimate, party,
				budget == null ? null : (long) budget * party, language, converter);
		CostEstimateResponse.ExchangeRateInfo exchangeRate = rate == null ? null
				: new CostEstimateResponse.ExchangeRateInfo(rate.currency(), rate.krwPerUnit(), rate.rateDate());
		return new CostEstimateResponse(
				tripService.toResponse(trip, language, converter.currency()),
				perPerson,
				total,
				guidance(perPerson.budget(), converter.currency(), language),
				limitations(estimate, language),
				sources(estimate, rate, language),
				exchangeRate);
	}

	/** 1인(multiplier 1) 또는 인원 합계(multiplier = 인원) 기준 경비. budgetKrw는 같은 기준의 희망 예산(원) */
	private CostEstimateResponse.CostBreakdown breakdown(CostEstimate estimate, int multiplier, Long budgetKrw,
			Language language, MoneyConverter converter) {
		CostEstimate.Range sum = estimate.total();
		long totalMid = sum.mid();

		List<CostEstimateResponse.ItemCost> items = new ArrayList<>();
		for (ExpenseItem item : DISPLAY_ORDER) {
			CostEstimate.Range range = estimate.item(item);
			items.add(new CostEstimateResponse.ItemCost(
					item,
					scaled(range, multiplier, converter),
					sharePct(range.mid(), totalMid),
					ExpenseLabels.source(estimate.sources().get(item), properties.referenceStayDays(), language).name()));
		}

		long peakMid = estimate.days().stream().mapToLong(day -> day.total().mid()).max().orElse(0);
		boolean peakAssigned = false;
		List<CostEstimateResponse.DayCost> days = new ArrayList<>();
		for (CostEstimate.Day day : estimate.days()) {
			CostEstimate.Range dayTotal = day.total();
			boolean highest = !peakAssigned && peakMid > 0 && dayTotal.mid() == peakMid;
			peakAssigned |= highest;
			List<CostEstimateResponse.ItemAmount> amounts = new ArrayList<>();
			for (ExpenseItem item : DISPLAY_ORDER) {
				CostEstimate.Range range = day.items().getOrDefault(item, CostEstimate.Range.ZERO);
				amounts.add(new CostEstimateResponse.ItemAmount(item, scaled(range, multiplier, converter)));
			}
			days.add(new CostEstimateResponse.DayCost(day.tripDay().getDayNumber(), day.tripDay().getTripDate(),
					scaled(dayTotal, multiplier, converter), amounts, highest));
		}

		long min = (long) sum.min() * multiplier;
		long max = (long) sum.max() * multiplier;
		CostEstimateResponse.Budget budget = budgetKrw == null ? null : budget(min, max, budgetKrw, converter);
		return new CostEstimateResponse.CostBreakdown(converter.range(min, max), budget, items, days);
	}

	/** 원화 범위 × 인원을 표시 통화 범위로 (int를 넘지 않게 long으로 곱한다) */
	private static MoneyRange scaled(CostEstimate.Range range, int multiplier, MoneyConverter converter) {
		return converter.range((long) range.min() * multiplier, (long) range.max() * multiplier);
	}

	/** 희망 예산 비교. 예산 0원이면 사용률은 null */
	private static CostEstimateResponse.Budget budget(long min, long max, long budget, MoneyConverter converter) {
		BudgetStatus status;
		if (max <= budget) {
			status = BudgetStatus.WITHIN_BUDGET;
		} else if (min > budget) {
			status = BudgetStatus.OVER_BUDGET;
		} else {
			status = BudgetStatus.MAY_EXCEED;
		}
		CostEstimateResponse.PercentRange usagePct = budget <= 0 ? null
				: new CostEstimateResponse.PercentRange(percent(min, budget), percent(max, budget));
		MoneyRange overBy = status == BudgetStatus.WITHIN_BUDGET ? null
				: converter.range(Math.max(0, min - budget), Math.max(0, max - budget));
		MoneyRange leftOver = status == BudgetStatus.OVER_BUDGET ? null
				: converter.range(Math.max(0, budget - max), Math.max(0, budget - min));
		return new CostEstimateResponse.Budget(converter.amount(budget), status, usagePct, overBy, leftOver);
	}

	/** value ÷ base × 100 (소수 첫째 자리, 반올림) */
	private static BigDecimal percent(long value, long base) {
		return BigDecimal.valueOf(value).multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(base), 1, RoundingMode.HALF_UP);
	}

	/** 전체 경비 대비 비중(%, 소수 첫째 자리). 중간값 기준 */
	private static BigDecimal sharePct(long itemMid, long totalMid) {
		if (totalMid <= 0) {
			return BigDecimal.ZERO.setScale(1);
		}
		return BigDecimal.valueOf(itemMid * 100L).divide(BigDecimal.valueOf(totalMid), 1, RoundingMode.HALF_UP);
	}

	/** 예산 조정 안내 (1인 기준). LLM을 붙이기 전까지는 상태별 템플릿 문장이다. 희망 예산이 없으면 null */
	private static String guidance(CostEstimateResponse.Budget budget, DisplayCurrency currency, Language language) {
		if (budget == null) {
			return null;
		}
		String unit = " " + currency.name();
		return switch (budget.status()) {
			case WITHIN_BUDGET -> language.pick(
					"예상 경비가 희망 예산 안에 있어요. 1인 약 " + range(budget.leftOver()) + unit + "가 남을 수 있어요.",
					"Your estimate fits your budget. About " + range(budget.leftOver()) + unit
							+ " per person may be left over.");
			case MAY_EXCEED -> language.pick(
					"일정에 따라 희망 예산을 1인 최대 " + format(budget.overBy().max()) + unit
							+ " 넘을 수 있어요. 쇼핑이나 입장료가 있는 장소를 줄이면 예산 안으로 맞출 수 있어요.",
					"Depending on your plans, you may exceed your budget by up to " + format(budget.overBy().max()) + unit
							+ " per person. Cutting back on shopping or paid attractions can keep you within budget.");
			case OVER_BUDGET -> language.pick(
					"예상 경비가 희망 예산보다 1인 " + range(budget.overBy()) + unit
							+ " 많아요. 일정을 줄이거나 예산을 늘려 보세요.",
					"Your estimate is " + range(budget.overBy()) + unit
							+ " per person over your budget. Try trimming the itinerary or raising your budget.");
		};
	}

	private static String range(MoneyRange range) {
		return format(range.min()) + "~" + format(range.max());
	}

	private static String format(BigDecimal amount) {
		return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(amount);
	}

	/** 데이터 한계 (화면에 그대로 쓰는 문장) */
	private static List<CostEstimateResponse.Limitation> limitations(CostEstimate estimate, Language language) {
		List<CostEstimateResponse.Limitation> result = new ArrayList<>();
		if (estimate.sources().containsValue(CostEstimate.Source.CARD_STATS)) {
			result.add(new CostEstimateResponse.Limitation("CARD_SPENDING_ONLY", language.pick(
					"식비·쇼핑·기타는 카드 결제 통계 기준이라 현금 지출은 들어 있지 않아요.",
					"Food, shopping and other costs are based on card payment statistics and exclude cash spending.")));
		}
		if (estimate.sources().containsValue(CostEstimate.Source.NO_DATA)) {
			result.add(new CostEstimateResponse.Limitation("NO_CARD_STATS", language.pick(
					"이 국적의 카드 소비 통계가 없어 식비·쇼핑·기타를 계산하지 못했어요.",
					"No card spending statistics for this nationality, so food, shopping and other costs are not estimated.")));
		}
		if (estimate.feeUnknownPlaces() > 0) {
			int n = estimate.feeUnknownPlaces();
			result.add(new CostEstimateResponse.Limitation("FEE_UNKNOWN", language.pick(
					"요금 정보가 없는 장소 " + n + "곳은 입장료를 0원으로 계산했어요.",
					"Admission is counted as 0 for " + n + (n == 1 ? " place" : " places") + " with no fee information.")));
		}
		if (estimate.fareUnavailableLegs() > 0) {
			int n = estimate.fareUnavailableLegs();
			result.add(new CostEstimateResponse.Limitation("FARE_UNAVAILABLE", language.pick(
					"운임을 받지 못한 이동 구간 " + n + "곳은 교통비에 들어 있지 않아요.",
					n + (n == 1 ? " leg" : " legs") + " without fare data " + (n == 1 ? "is" : "are")
							+ " not included in transportation.")));
		}
		return result;
	}

	/** 근거 출처 (통계 기간·환율 고시일) */
	private static List<CostEstimateResponse.Source> sources(CostEstimate estimate, ExchangeService.Rate rate,
			Language language) {
		List<CostEstimateResponse.Source> result = new ArrayList<>();
		if (estimate.sources().containsValue(CostEstimate.Source.CARD_STATS)) {
			result.add(new CostEstimateResponse.Source(
					language.pick("한국관광 데이터랩 방한 외국인 카드 소비 통계", "KTO Data Lab foreign visitor card spending"),
					period(estimate.statMonths())));
		}
		result.add(new CostEstimateResponse.Source(language.pick("ODsay 대중교통 운임", "ODsay transit fares"), null));
		result.add(new CostEstimateResponse.Source(
				language.pick("한국관광공사 TourAPI 이용 요금", "KTO TourAPI admission fees"), null));
		if (rate != null) {
			result.add(new CostEstimateResponse.Source(
					language.pick("한국수출입은행 매매기준율", "Korea Eximbank exchange rate"), rate.rateDate().toString()));
		}
		return result;
	}

	/** 통계 기간 "2025-01 ~ 2025-06" (한 달이면 그 달만). 없으면 null */
	private static String period(List<String> months) {
		if (months.isEmpty()) {
			return null;
		}
		String first = month(months.get(0));
		String last = month(months.get(months.size() - 1));
		return first.equals(last) ? first : first + " ~ " + last;
	}

	/** "202501" → "2025-01" (다른 형식은 그대로) */
	private static String month(String ym) {
		return ym.length() == 6 ? ym.substring(0, 4) + "-" + ym.substring(4) : ym;
	}
}
