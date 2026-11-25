package com.tukorea.gdgoc.koready.domain.expense.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.domain.expense.dto.ExpenseReportResponse.ComparisonStatus;
import org.junit.jupiter.api.Test;

/** 예상 범위와 실제 지출 비교 (잠정 기준값: 범위 가운데값) */
class ExpenseComparisonTest {

	@Test
	void statusComparesWithRangeBounds() {
		assertThat(ExpenseComparison.status(10_000, 20_000, 9_999)).isEqualTo(ComparisonStatus.BELOW);
		assertThat(ExpenseComparison.status(10_000, 20_000, 10_000)).isEqualTo(ComparisonStatus.WITHIN);
		assertThat(ExpenseComparison.status(10_000, 20_000, 20_000)).isEqualTo(ComparisonStatus.WITHIN);
		assertThat(ExpenseComparison.status(10_000, 20_000, 20_001)).isEqualTo(ComparisonStatus.ABOVE);
	}

	@Test
	void differenceIsAgainstRangeMiddle() {
		// 기준값 15,000원
		assertThat(ExpenseComparison.difference(10_000, 20_000, 12_000)).isEqualTo(-3_000);
		assertThat(ExpenseComparison.differencePct(10_000, 20_000, 12_000)).isEqualByComparingTo("-20.0");
		assertThat(ExpenseComparison.errorRatePct(10_000, 20_000, 12_000)).isEqualByComparingTo("20.0");
		assertThat(ExpenseComparison.accuracyPct(10_000, 20_000, 12_000)).isEqualByComparingTo("80.0");
		assertThat(ExpenseComparison.executionPct(10_000, 20_000, 12_000)).isEqualByComparingTo("80.0");
	}

	@Test
	void accuracyDoesNotGoBelowZero() {
		// 기준값의 3배를 쓰면 오차율 200%
		assertThat(ExpenseComparison.errorRatePct(10_000, 10_000, 30_000)).isEqualByComparingTo("200.0");
		assertThat(ExpenseComparison.accuracyPct(10_000, 10_000, 30_000)).isEqualByComparingTo("0.0");
	}

	@Test
	void ratiosAreNullWhenBaselineIsZero() {
		assertThat(ExpenseComparison.differencePct(0, 0, 5_000)).isNull();
		assertThat(ExpenseComparison.accuracyPct(0, 0, 5_000)).isNull();
		assertThat(ExpenseComparison.executionPct(0, 0, 5_000)).isNull();
		assertThat(ExpenseComparison.difference(0, 0, 5_000)).isEqualTo(5_000);
	}
}
