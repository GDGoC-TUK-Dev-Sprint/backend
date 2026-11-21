package com.tukorea.gdgoc.koready.domain.expense.service;

import com.tukorea.gdgoc.koready.domain.expense.entity.ExpenseItem;
import com.tukorea.gdgoc.koready.global.dto.Code;
import com.tukorea.gdgoc.koready.global.web.Language;

/** 경비 항목·근거 데이터의 화면 이름 */
final class ExpenseLabels {

	private ExpenseLabels() {}

	static Code item(ExpenseItem item, Language language) {
		return new Code(item.name(), switch (item) {
			case FOOD -> language.pick("식비", "Food");
			case SIGHTSEEING -> language.pick("관광·입장료", "Sightseeing & admission");
			case TRANSPORT -> language.pick("교통", "Transportation");
			case SHOPPING -> language.pick("쇼핑", "Shopping");
			case OTHER -> language.pick("기타", "Other");
		});
	}

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
}
