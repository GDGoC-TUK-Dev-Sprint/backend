package com.tukorea.gdgoc.koready.domain.expense.service;

import com.tukorea.gdgoc.koready.domain.expense.entity.ExpenseItem;
import com.tukorea.gdgoc.koready.global.dto.Code;
import com.tukorea.gdgoc.koready.global.web.Language;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * 경비 항목 근거 데이터의 설명 문장 (응답의 항목 이름은 코드만 주고 프론트가 매핑한다).
 * 서버가 만드는 문장(예산 안내·인사이트) 안의 항목 이름과 금액 표기도 여기서 만든다.
 */
final class ExpenseLabels {

	private ExpenseLabels() {}

	static Code source(CostEstimate.Source source, int referenceStayDays, Language language) {
		return new Code(source.name(), switch (source) {
			case CARD_STATS -> language.pick(
					"한국관광 데이터랩 방한 외국인 카드 소비 통계 (1인 평균 ÷ 체류일 " + referenceStayDays + "일 가정)",
					"KTO Data Lab foreign visitor card spending (per-visitor average ÷ assumed " + referenceStayDays + "-day stay)");
			case ROUTE_FARES -> language.pick("이동 구간 대중교통 운임 (택시 제외)", "Transit fares per leg (taxi excluded)");
			case PLACE_FEES -> language.pick("장소별 성인 입장료", "Adult admission fee per place");
			case NO_DATA -> language.pick("이 국적의 통계 데이터가 없습니다", "No statistics for this nationality");
		});
	}

	/** 문장 안에 쓰는 항목 이름 */
	static String item(ExpenseItem item, Language language) {
		return switch (item) {
			case FOOD -> language.pick("식비", "food");
			case TRANSPORT -> language.pick("교통", "transportation");
			case SHOPPING -> language.pick("쇼핑", "shopping");
			case SIGHTSEEING -> language.pick("관광·입장료", "sightseeing");
			case OTHER -> language.pick("기타", "other expenses");
		};
	}

	/** 문장 안에 쓰는 금액 "12,345" · "12.5" (통화 코드는 붙이지 않는다) */
	static String amount(BigDecimal amount) {
		return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(amount);
	}
}
