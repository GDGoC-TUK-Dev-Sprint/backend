package com.tukorea.gdgoc.koready.domain.expense.dto;

import com.tukorea.gdgoc.koready.domain.expense.entity.ExpenseItem;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripResponse;
import com.tukorea.gdgoc.koready.global.dto.Money;
import java.math.BigDecimal;
import java.util.List;

/**
 * 지출 리포트 (API 명세 30, 09-01~07). 저장하지 않고 부를 때 계산한다. 금액은 표시 통화로 환산한 값이다.
 * 1인(perPerson)과 인원 합계(total)를 둘 다 주므로 09-02 전환은 다시 요청하지 않는다.
 *
 * @param insight 09-06 인사이트 문구 (가장 크게 절약·초과한 항목, 1인 기준). 비교할 항목이 없으면 null
 */
public record ExpenseReportResponse(TripResponse trip, ReportBreakdown perPerson, ReportBreakdown total, String insight) {

	/**
	 * 기준(1인 / 인원 합계)별 리포트. 비율은 % (소수 첫째 자리)이고 비교 기준값이 0원이면 null이다.
	 *
	 * @param estimate      예상 경비 합계 (확정 때 저장한 값)
	 * @param actual        실제 지출 합계
	 * @param difference    09-03 절약·초과 금액 (음수면 절약)
	 * @param differencePct 09-03 절약·초과 비율
	 * @param accuracyPct   09-03 예측 일치도
	 * @param errorRatePct  09-03 오차율
	 * @param executionPct  09-04 예산 집행률 (실제 ÷ 예상)
	 */
	public record ReportBreakdown(
			MoneyRange estimate,
			Money actual,
			ComparisonStatus status,
			Money difference,
			BigDecimal differencePct,
			BigDecimal accuracyPct,
			BigDecimal errorRatePct,
			BigDecimal executionPct,
			List<ReportItem> items) {
	}

	/**
	 * 항목별 비교 (09-05·07). 기타 항목은 하나(OTHER)로 더한다. 입력하지 않은 항목은 actual이, 확정 때 예상이 없는 항목은 estimate가
	 * null이고, 둘 중 하나가 없으면 status·difference도 null이다.
	 *
	 * @param labels   기타 항목 이름들 (OTHER만, 다른 항목은 빈 목록)
	 * @param sharePct 실제 지출 비중 (실제 지출이 없으면 null)
	 */
	public record ReportItem(
			ExpenseItem item,
			List<String> labels,
			MoneyRange estimate,
			Money actual,
			ComparisonStatus status,
			Money difference,
			BigDecimal sharePct) {
	}

	/** BELOW: 예상 최소보다 적음 / WITHIN: 범위 안 / ABOVE: 예상 최대보다 많음 */
	public enum ComparisonStatus { BELOW, WITHIN, ABOVE }
}
