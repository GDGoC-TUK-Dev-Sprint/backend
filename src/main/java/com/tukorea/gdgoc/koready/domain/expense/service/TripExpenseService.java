package com.tukorea.gdgoc.koready.domain.expense.service;

import com.tukorea.gdgoc.koready.domain.exchange.service.ExchangeService;
import com.tukorea.gdgoc.koready.domain.expense.config.ExpenseProperties;
import com.tukorea.gdgoc.koready.domain.expense.dto.ExpenseReportResponse;
import com.tukorea.gdgoc.koready.domain.expense.dto.ExpenseReportResponse.ComparisonStatus;
import com.tukorea.gdgoc.koready.domain.expense.dto.ExpenseSaveRequest;
import com.tukorea.gdgoc.koready.domain.expense.dto.ExpenseSheetResponse;
import com.tukorea.gdgoc.koready.domain.expense.dto.MoneyRange;
import com.tukorea.gdgoc.koready.domain.expense.entity.ExpenseItem;
import com.tukorea.gdgoc.koready.domain.expense.entity.TripCostEstimate;
import com.tukorea.gdgoc.koready.domain.expense.entity.TripExpense;
import com.tukorea.gdgoc.koready.domain.expense.repository.TripCostEstimateRepository;
import com.tukorea.gdgoc.koready.domain.expense.repository.TripExpenseRepository;
import com.tukorea.gdgoc.koready.domain.trip.entity.Trip;
import com.tukorea.gdgoc.koready.domain.trip.service.TripService;
import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.dto.Money;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import com.tukorea.gdgoc.koready.global.exception.FieldErrors;
import com.tukorea.gdgoc.koready.global.web.Language;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여행 후 실제 지출: 지출 입력 화면(API 명세 28)·지출 저장(29)·지출 리포트(30). 끝난(종료일이 지난) 확정 여행만 받는다.
 * 실제 지출은 원화·인원 합계로 저장하고, 응답만 표시 통화로 환산한다. 예상 경비는 확정할 때 저장한 값을 쓴다.
 */
@Service
@RequiredArgsConstructor
public class TripExpenseService {

	/** 기타 항목 이름 최대 글자 수 ({@code trip_expenses.label}) */
	private static final int LABEL_MAX_LENGTH = 50;
	/** {@code trip_expenses.amount_krw}(integer) 한도 */
	private static final BigDecimal MAX_AMOUNT_KRW = BigDecimal.valueOf(Integer.MAX_VALUE);

	private final TripService tripService;
	private final TripCostEstimateRepository costEstimates;
	private final TripExpenseRepository expenses;
	private final ExchangeService exchangeService;
	private final ExpenseProperties properties;
	private final Clock clock;

	/** 지출 입력 화면 (28). 저장하지 않았으면 expenses가 비어 있다 */
	@Transactional(readOnly = true)
	public ExpenseSheetResponse getSheet(Long userId, Long tripId, Language language, DisplayCurrency currency) {
		Trip trip = completedTrip(tripService.findMyTrip(userId, tripId));
		MoneyConverter converter = converter(currency);
		return sheet(trip, expenses.findByTripId(trip.getId()), language, converter);
	}

	/**
	 * 실제 지출 저장 (29). 금액은 오늘(서울) 이전 가장 최근 고시일 환율로 원화로 바꾸고, 1인 기준이면 인원을 곱해 인원 합계로 저장한다.
	 * 그 여행의 지출을 지우고 보낸 순서대로 다시 넣는다 (통째로 덮어씀). 겹친 저장이 섞이지 않게 사용자 행을 잠근다.
	 * 여행이 없거나(404) 끝난 확정 여행이 아니면(409) 요청 값을 보기 전에 거절한다.
	 */
	@Transactional
	public ExpenseSheetResponse save(Long userId, Long tripId, ExpenseSaveRequest request, Language language,
			DisplayCurrency currency) {
		Trip trip = completedTrip(tripService.lockMyTrip(userId, tripId));
		List<TripExpense> rows = toRows(trip, request);
		MoneyConverter converter = converter(currency);
		expenses.deleteByTripId(trip.getId());
		return sheet(trip, expenses.saveAll(rows), language, converter);
	}

	/** 지출 리포트 (30). 부를 때 계산하고 저장하지 않는다. 지출을 저장하기 전이면 {@code 409 EXPENSES_NOT_SAVED} */
	@Transactional(readOnly = true)
	public ExpenseReportResponse getReport(Long userId, Long tripId, Language language, DisplayCurrency currency) {
		Trip trip = completedTrip(tripService.findMyTrip(userId, tripId));
		List<TripExpense> rows = expenses.findByTripId(trip.getId());
		if (rows.isEmpty()) {
			throw new ApiException(ErrorCode.EXPENSES_NOT_SAVED);
		}
		MoneyConverter converter = converter(currency);
		Spending spending = Spending.of(costEstimates.findByTripId(trip.getId()), rows, trip.getPartySize());
		return new ExpenseReportResponse(
				tripService.toResponse(trip, language, converter.currency()),
				breakdown(spending, Basis.PER_PERSON, converter),
				breakdown(spending, Basis.TOTAL, converter),
				insight(spending, converter, language));
	}

	/** 끝난(종료일이 지난) 확정 여행이 아니면 {@code 409 TRIP_NOT_COMPLETED} */
	private Trip completedTrip(Trip trip) {
		if (!trip.isCompletedOn(LocalDate.now(clock))) {
			throw new ApiException(ErrorCode.TRIP_NOT_COMPLETED);
		}
		return trip;
	}

	/** 표시 통화 환산기. 외화인데 환율이 없으면 {@code 503 EXCHANGE_RATE_UNAVAILABLE} */
	private MoneyConverter converter(DisplayCurrency currency) {
		return currency == DisplayCurrency.KRW ? MoneyConverter.krw()
				: MoneyConverter.of(currency, exchangeService.rate(currency).krwPerUnit());
	}

	// ---- 29 저장

	/** 요청을 검사해 저장할 행으로 바꾼다. 형식 오류를 모두 모아 400을 던진 뒤 원화로 바꾼다 */
	private List<TripExpense> toRows(Trip trip, ExpenseSaveRequest request) {
		List<ExpenseSaveRequest.Item> items = request.items();
		FieldErrors errors = new FieldErrors();
		if (items.isEmpty()) {
			errors.add("items", "1개 이상이어야 합니다");
		}
		Set<ExpenseItem> seen = EnumSet.noneOf(ExpenseItem.class);
		int others = 0;
		for (int i = 0; i < items.size(); i++) {
			ExpenseSaveRequest.Item item = items.get(i);
			String field = "items[" + i + "]";
			if (item.item() == ExpenseItem.OTHER) {
				others++;
				checkLabel(item.label(), field + ".label", errors);
			} else if (!seen.add(item.item())) {
				errors.add(field + ".item", "기타 말고는 항목마다 한 번만 보낼 수 있습니다");
			}
			checkAmount(item.amount(), field + ".amount.amount", errors);
		}
		if (others > properties.maxOtherItems()) {
			errors.add("items", "기타 항목은 " + properties.maxOtherItems() + "개까지입니다");
		}
		errors.throwIfAny();

		int multiplier = request.basis() == ExpenseSaveRequest.Basis.PER_PERSON ? trip.getPartySize() : 1;
		// 환율은 통화마다 한 번만 읽는다 (항목마다 통화가 다를 수 있다)
		Map<DisplayCurrency, BigDecimal> krwPerUnit = new EnumMap<>(DisplayCurrency.class);
		List<TripExpense> rows = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			ExpenseSaveRequest.Item item = items.get(i);
			BigDecimal rate = krwPerUnit.computeIfAbsent(item.amount().currency(), exchangeService::krwPerUnit);
			long krw = exchangeService.toKrw(item.amount(), rate) * multiplier;
			if (krw > Integer.MAX_VALUE) {
				errors.add("items[" + i + "].amount.amount", "너무 큽니다");
				continue;
			}
			String label = item.item() == ExpenseItem.OTHER ? item.label().strip() : null;
			rows.add(TripExpense.of(trip, item.item(), label, (int) krw, i));
		}
		errors.throwIfAny();
		return rows;
	}

	/** 기타 항목 이름: 앞뒤 공백을 뺀 1~50자. 다른 항목의 이름은 쓰지 않는다 */
	private static void checkLabel(String label, String field, FieldErrors errors) {
		String stripped = label == null ? "" : label.strip();
		if (stripped.isEmpty()) {
			errors.add(field, "기타 항목은 이름이 필요합니다");
		} else if (stripped.codePointCount(0, stripped.length()) > LABEL_MAX_LENGTH) {
			errors.add(field, LABEL_MAX_LENGTH + "자 이하여야 합니다");
		}
	}

	/** 금액 형식: 원·엔은 정수, 달러·유로는 소수 둘째 자리까지 (0 이상은 Bean Validation이 본다) */
	private static void checkAmount(Money amount, String field, FieldErrors errors) {
		if (!amount.currency().fits(amount.amount())) {
			errors.add(field, switch (amount.currency()) {
				case KRW -> "원 단위 정수여야 합니다";
				case JPY -> "엔 단위 정수여야 합니다";
				case USD, EUR -> "소수 둘째 자리까지여야 합니다";
			});
		} else if (amount.amount().compareTo(MAX_AMOUNT_KRW) > 0) {
			// 표시 통화는 모두 1단위가 1원 이상이라, 금액만으로 한도를 넘으면 원화로 바꿔도 넘는다
			errors.add(field, "너무 큽니다");
		}
	}

	// ---- 28 지출 입력 화면

	private ExpenseSheetResponse sheet(Trip trip, List<TripExpense> rows, Language language, MoneyConverter converter) {
		int party = trip.getPartySize();
		List<ExpenseSheetResponse.ItemEstimate> estimates = new ArrayList<>();
		long minSum = 0;
		long maxSum = 0;
		for (TripCostEstimate estimate : inDisplayOrder(costEstimates.findByTripId(trip.getId()))) {
			long min = estimate.getMinKrw();
			long max = estimate.getMaxKrw();
			minSum += min;
			maxSum += max;
			estimates.add(new ExpenseSheetResponse.ItemEstimate(estimate.getItem(),
					converter.range(min, max), converter.range(min * party, max * party)));
		}
		Integer budget = trip.getBudgetPerPersonKrw();
		List<ExpenseSheetResponse.ItemExpense> spent = rows.stream()
				.map(row -> new ExpenseSheetResponse.ItemExpense(row.getItem(), row.getLabel(),
						converter.amount(perPerson(row.getAmountKrw(), party)), converter.amount(row.getAmountKrw())))
				.toList();
		return new ExpenseSheetResponse(
				tripService.toResponse(trip, language, converter.currency()),
				estimates,
				new ExpenseSheetResponse.EstimateTotal(
						converter.range(minSum, maxSum), converter.range(minSum * party, maxSum * party)),
				budget == null ? null
						: new ExpenseSheetResponse.Budget(converter.amount(budget), converter.amount((long) budget * party)),
				spent,
				!rows.isEmpty());
	}

	private static List<TripCostEstimate> inDisplayOrder(List<TripCostEstimate> estimates) {
		return estimates.stream()
				.sorted(Comparator.comparingInt(e -> ExpenseService.DISPLAY_ORDER.indexOf(e.getItem())))
				.toList();
	}

	/** 인원 합계를 1인 금액으로 (원 단위 반올림) */
	private static long perPerson(long totalKrw, int party) {
		return BigDecimal.valueOf(totalKrw).divide(BigDecimal.valueOf(party), 0, RoundingMode.HALF_UP).longValueExact();
	}

	// ---- 30 지출 리포트

	private enum Basis { PER_PERSON, TOTAL }

	/**
	 * 리포트 계산 재료 (원). 예상은 항목별 1인 범위, 실제는 항목별 인원 합계이고 기타 항목은 하나로 더했다.
	 *
	 * @param estimates 확정 때 저장한 항목별 1인 예상 [최소, 최대]
	 * @param actuals   항목별 실제 지출 (인원 합계)
	 * @param labels    기타 항목 이름들 (보낸 순서)
	 */
	private record Spending(Map<ExpenseItem, long[]> estimates, Map<ExpenseItem, Long> actuals, List<String> labels,
			int party) {

		static Spending of(List<TripCostEstimate> estimateRows, List<TripExpense> expenseRows, int party) {
			Map<ExpenseItem, long[]> estimates = new EnumMap<>(ExpenseItem.class);
			for (TripCostEstimate row : estimateRows) {
				estimates.put(row.getItem(), new long[] {row.getMinKrw(), row.getMaxKrw()});
			}
			Map<ExpenseItem, Long> actuals = new EnumMap<>(ExpenseItem.class);
			List<String> labels = new ArrayList<>();
			for (TripExpense row : expenseRows) {
				actuals.merge(row.getItem(), (long) row.getAmountKrw(), Long::sum);
				if (row.getLabel() != null) {
					labels.add(row.getLabel());
				}
			}
			return new Spending(estimates, actuals, List.copyOf(labels), party);
		}

		/** 비교할 항목 (예상이나 실제가 있는 것, 화면 순서) */
		List<ExpenseItem> items() {
			return ExpenseService.DISPLAY_ORDER.stream()
					.filter(item -> estimates.containsKey(item) || actuals.containsKey(item))
					.toList();
		}

		/** 기준별 예상 [최소, 최대]. 없으면 null */
		long[] estimate(ExpenseItem item, Basis basis) {
			long[] range = estimates.get(item);
			if (range == null) {
				return null;
			}
			int multiplier = basis == Basis.TOTAL ? party : 1;
			return new long[] {range[0] * multiplier, range[1] * multiplier};
		}

		/** 기준별 실제. 입력하지 않은 항목은 null */
		Long actual(ExpenseItem item, Basis basis) {
			Long total = actuals.get(item);
			return total == null ? null : forBasis(total, basis);
		}

		long[] estimateTotal(Basis basis) {
			long min = 0;
			long max = 0;
			for (ExpenseItem item : estimates.keySet()) {
				long[] range = estimate(item, basis);
				min += range[0];
				max += range[1];
			}
			return new long[] {min, max};
		}

		long actualTotal(Basis basis) {
			return forBasis(actuals.values().stream().mapToLong(Long::longValue).sum(), basis);
		}

		private long forBasis(long totalKrw, Basis basis) {
			return basis == Basis.TOTAL ? totalKrw : perPerson(totalKrw, party);
		}
	}

	private static ExpenseReportResponse.ReportBreakdown breakdown(Spending spending, Basis basis,
			MoneyConverter converter) {
		long[] estimate = spending.estimateTotal(basis);
		long actual = spending.actualTotal(basis);
		long min = estimate[0];
		long max = estimate[1];

		List<ExpenseReportResponse.ReportItem> items = new ArrayList<>();
		for (ExpenseItem item : spending.items()) {
			long[] itemEstimate = spending.estimate(item, basis);
			Long itemActual = spending.actual(item, basis);
			boolean comparable = itemEstimate != null && itemActual != null;
			items.add(new ExpenseReportResponse.ReportItem(
					item,
					item == ExpenseItem.OTHER ? spending.labels() : List.of(),
					itemEstimate == null ? null : converter.range(itemEstimate[0], itemEstimate[1]),
					itemActual == null ? null : converter.amount(itemActual),
					comparable ? ExpenseComparison.status(itemEstimate[0], itemEstimate[1], itemActual) : null,
					comparable ? converter.amount(ExpenseComparison.difference(itemEstimate[0], itemEstimate[1], itemActual))
							: null,
					itemActual == null ? null : ExpenseComparison.percent(itemActual, actual)));
		}

		return new ExpenseReportResponse.ReportBreakdown(
				converter.range(min, max),
				converter.amount(actual),
				ExpenseComparison.status(min, max, actual),
				converter.amount(ExpenseComparison.difference(min, max, actual)),
				ExpenseComparison.differencePct(min, max, actual),
				ExpenseComparison.accuracyPct(min, max, actual),
				ExpenseComparison.errorRatePct(min, max, actual),
				ExpenseComparison.executionPct(min, max, actual),
				items);
	}

	/**
	 * 인사이트 문구 (1인 기준): 예상보다 가장 크게 덜 쓴 항목과 가장 크게 더 쓴 항목. LLM을 붙이기 전까지는 템플릿 문장이고,
	 * 숫자는 계산값을 그대로 넣는다. 예상과 실제가 둘 다 있는 항목이 없으면 null
	 */
	private static String insight(Spending spending, MoneyConverter converter, Language language) {
		ExpenseItem saved = null;
		ExpenseItem over = null;
		long savedBy = 0;
		long overBy = 0;
		boolean comparable = false;
		for (ExpenseItem item : spending.items()) {
			long[] estimate = spending.estimate(item, Basis.PER_PERSON);
			Long actual = spending.actual(item, Basis.PER_PERSON);
			if (estimate == null || actual == null) {
				continue;
			}
			comparable = true;
			long difference = ExpenseComparison.difference(estimate[0], estimate[1], actual);
			if (difference < -savedBy) {
				saved = item;
				savedBy = -difference;
			} else if (difference > overBy) {
				over = item;
				overBy = difference;
			}
		}
		if (!comparable) {
			return null;
		}
		String unit = " " + converter.currency().name();
		List<String> sentences = new ArrayList<>();
		// 표시 통화로 바꿔 0이 되는 차이(예: 5원 → 0.00 USD)는 문장으로 만들지 않는다
		if (saved != null && converter.amount(savedBy).amount().signum() != 0) {
			String amount = ExpenseLabels.amount(converter.amount(savedBy).amount()) + unit;
			sentences.add(language.pick(
					ExpenseLabels.item(saved, language) + "에서 예상보다 1인 " + amount + " 덜 썼어요.",
					"You spent " + amount + " less per person than expected on " + ExpenseLabels.item(saved, language) + "."));
		}
		if (over != null && converter.amount(overBy).amount().signum() != 0) {
			String amount = ExpenseLabels.amount(converter.amount(overBy).amount()) + unit;
			sentences.add(language.pick(
					ExpenseLabels.item(over, language) + "에서 예상보다 1인 " + amount + " 더 썼어요.",
					"You spent " + amount + " more per person than expected on " + ExpenseLabels.item(over, language) + "."));
		}
		if (sentences.isEmpty()) {
			return language.pick("모든 항목을 예상한 만큼 썼어요.", "You spent about as expected on every item.");
		}
		return String.join(" ", sentences);
	}
}
