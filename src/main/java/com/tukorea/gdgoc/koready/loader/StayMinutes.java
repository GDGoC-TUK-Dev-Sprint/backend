package com.tukorea.gdgoc.koready.loader;

import com.tukorea.gdgoc.koready.place.StayBasis;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 장소 체류시간(분). TourAPI 체류시간 값이 분으로 읽히면 그 값, 아니면 중분류별 기본값, 숙박은 0.
 */
final class StayMinutes {

	record Result(int minutes, StayBasis basis) {
	}

	/** 중분류별 기본값(분). AI Hub 국내 여행로그(수도권) 2023 서울 방문 기록의 체류시간 중앙값 */
	private static final Map<String, Integer> CLASS_MINUTES = Map.ofEntries(
			// 그 중분류 장소의 방문 기록
			Map.entry("VE07", 60), Map.entry("VE03", 60), Map.entry("HS01", 60), Map.entry("VE06", 120),
			Map.entry("SH06", 30), Map.entry("SH05", 60), Map.entry("VE04", 30), Map.entry("VE01", 60),
			Map.entry("SH02", 60), Map.entry("SH01", 90), Map.entry("FD01", 60), Map.entry("FD02", 60),
			Map.entry("FD05", 30),
			// 기록이 적어 대응하는 방문지 유형의 기록
			Map.entry("FD03", 60),
			Map.entry("HS02", 60), Map.entry("HS03", 60), Map.entry("HS04", 60),
			Map.entry("NA01", 60), Map.entry("NA02", 60), Map.entry("NA03", 60), Map.entry("NA04", 60),
			Map.entry("NA05", 60),
			Map.entry("EX01", 60), Map.entry("EX02", 60), Map.entry("EX04", 60), Map.entry("EX05", 60),
			Map.entry("EX06", 60), Map.entry("EX07", 60),
			Map.entry("VE05", 60), Map.entry("VE02", 120), Map.entry("VE09", 90), Map.entry("VE12", 90),
			Map.entry("EV01", 90), Map.entry("EV02", 90), Map.entry("EV03", 90),
			Map.entry("LS01", 120), Map.entry("LS02", 120), Map.entry("LS03", 120), Map.entry("LS04", 120),
			Map.entry("VE10", 120),
			Map.entry("SH07", 30));

	/** 표에 없는 중분류: 서울 방문 기록 전체 중앙값 */
	private static final int OTHER_CLASS_MINUTES = 60;

	/** "약 1시간 30분", "60분 내외"처럼 값 하나인 것만. 범위("1~2시간")·"공연별 상이"는 읽지 않는다 */
	private static final Pattern SINGLE_DURATION =
			Pattern.compile("^(?:약\\s*)?(?:(\\d+)\\s*시간)?\\s*(?:(\\d+)\\s*분)?\\s*(?:내외|정도)?$");

	private StayMinutes() {
	}

	static Result of(String class1Code, String class2Code, String tourApiText) {
		if ("AC".equals(class1Code)) {
			return new Result(0, StayBasis.CLASS);
		}
		Integer parsed = parse(tourApiText);
		if (parsed != null) {
			return new Result(parsed, StayBasis.TOURAPI);
		}
		return new Result(CLASS_MINUTES.getOrDefault(class2Code, OTHER_CLASS_MINUTES), StayBasis.CLASS);
	}

	/** 분으로 읽을 수 없으면 null */
	static Integer parse(String text) {
		if (text == null) {
			return null;
		}
		Matcher m = SINGLE_DURATION.matcher(text.strip());
		if (!m.matches() || (m.group(1) == null && m.group(2) == null)) {
			return null;
		}
		int hours = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
		int minutes = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
		int total = hours * 60 + minutes;
		return total > 0 ? total : null;
	}
}
