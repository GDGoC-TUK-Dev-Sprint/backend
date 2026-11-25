package com.tukorea.gdgoc.koready.domain.expense.service;

import com.tukorea.gdgoc.koready.domain.expense.dto.ExpenseReportResponse.ComparisonStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 예상 경비 범위와 실제 지출의 비교 (API 명세 30). 금액은 원 단위다.
 * <p>
 * 범위의 어느 값과 비교할지, 예측 일치도·오차율 계산식은 명세에서 정하지 않았다(정해져도 응답 모양은 같다). 정하기 전까지는 아래 잠정 규칙을 쓰고,
 * 정하면 이 클래스만 바꾼다.
 * <ul>
 *   <li>비교 기준값: 범위 가운데값 ((최소 + 최대) ÷ 2)</li>
 *   <li>차이 = 실제 − 기준값 (음수면 절약), 차이 비율 = 차이 ÷ 기준값</li>
 *   <li>오차율 = |실제 − 기준값| ÷ 기준값, 예측 일치도 = 100 − 오차율 (0 아래로는 내려가지 않는다)</li>
 *   <li>예산 집행률 = 실제 ÷ 기준값</li>
 * </ul>
 * 비율은 % 소수 첫째 자리(반올림)이고, 기준값이 0원이면 나눌 수 없어 null이다.
 */
final class ExpenseComparison {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private ExpenseComparison() {}

	/** 비교 기준값 (잠정: 범위 가운데값) */
	static long baseline(long min, long max) {
		return (min + max) / 2;
	}

	/** 범위 안팎. 범위 경계와 같으면 WITHIN */
	static ComparisonStatus status(long min, long max, long actual) {
		if (actual < min) {
			return ComparisonStatus.BELOW;
		}
		return actual > max ? ComparisonStatus.ABOVE : ComparisonStatus.WITHIN;
	}

	/** 실제 − 기준값 (음수면 절약) */
	static long difference(long min, long max, long actual) {
		return actual - baseline(min, max);
	}

	static BigDecimal differencePct(long min, long max, long actual) {
		return percent(difference(min, max, actual), baseline(min, max));
	}

	static BigDecimal errorRatePct(long min, long max, long actual) {
		return percent(Math.abs(difference(min, max, actual)), baseline(min, max));
	}

	static BigDecimal accuracyPct(long min, long max, long actual) {
		BigDecimal errorRate = errorRatePct(min, max, actual);
		return errorRate == null ? null : HUNDRED.subtract(errorRate).max(BigDecimal.ZERO).setScale(1);
	}

	static BigDecimal executionPct(long min, long max, long actual) {
		return percent(actual, baseline(min, max));
	}

	/** value ÷ base × 100 (소수 첫째 자리, 반올림). base가 0 이하면 null */
	static BigDecimal percent(long value, long base) {
		if (base <= 0) {
			return null;
		}
		return BigDecimal.valueOf(value).multiply(HUNDRED).divide(BigDecimal.valueOf(base), 1, RoundingMode.HALF_UP);
	}
}
