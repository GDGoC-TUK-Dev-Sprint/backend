package com.tukorea.gdgoc.koready.domain.expense.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 예상 경비 계산 설정 ({@code koready.expense.*}).
 *
 * @param referenceStayDays 카드 소비 통계의 "1인 평균 소비"를 하루 단위로 바꿀 때 나누는 체류일 가정값 (임시값 — 팀에서 근거 확인 필요)
 * @param statMonths        통계 범위를 잡을 때 쓰는 최근 월 수 (월별 1일 1인 평균의 최소~최대가 경비 범위가 된다)
 */
@ConfigurationProperties(prefix = "koready.expense")
public record ExpenseProperties(
		@DefaultValue("7") int referenceStayDays,
		@DefaultValue("12") int statMonths) {
}
