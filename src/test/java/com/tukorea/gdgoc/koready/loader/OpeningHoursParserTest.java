package com.tukorea.gdgoc.koready.loader;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.loader.OpeningHoursParser.Result;
import com.tukorea.gdgoc.koready.loader.OpeningHoursParser.Row;
import com.tukorea.gdgoc.koready.place.HoursType;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpeningHoursParserTest {

	private static final int ATTRACTION = 12;
	private static final int FESTIVAL = 15;

	private static Result parse(String hours, String rest) {
		return OpeningHoursParser.parse(hours, rest, ATTRACTION);
	}

	private static List<Integer> days(Result r) {
		return r.rows().stream().map(Row::dayOfWeek).toList();
	}

	private static Row day(Result r, int dayOfWeek) {
		return r.rows().stream().filter(row -> row.dayOfWeek() == dayOfWeek).findFirst().orElseThrow();
	}

	@Test
	void restRemovesWeeklyClosedDays() {
		Result r = parse("09:00~18:00 (입장마감 17:30)", "매주 월요일 (단, 월요일이 공휴일인 경우 그 다음날 휴관) / 1월 1일 / 설·추석 연휴");
		assertThat(r.type()).isEqualTo(HoursType.STRUCTURED);
		assertThat(days(r)).containsExactly(2, 3, 4, 5, 6, 7);
		assertThat(day(r, 2)).isEqualTo(new Row(2, null, null, LocalTime.of(9, 0), LocalTime.of(18, 0), LocalTime.of(17, 30), null, null));
	}

	@Test
	void readsWeekdayAndWeekendWithoutLineBreaks() {
		Result r = parse("- 평일 13:30~17:30 (입장 마감 17:20)- 주말 09:00~17:30 (입장 마감 17:20)", "공휴일");
		assertThat(days(r)).containsExactly(1, 2, 3, 4, 5, 6, 7);
		assertThat(day(r, 1).open()).isEqualTo(LocalTime.of(13, 30));
		assertThat(day(r, 6).open()).isEqualTo(LocalTime.of(9, 0));
	}

	@Test
	void readsBreakAndLastOrder() {
		Result r = parse("- 11:00~22:00<br>\n- 준비시간 15:00~17:00<br>\n- 마지막 주문 21:00", "매주 월요일");
		assertThat(days(r)).containsExactly(2, 3, 4, 5, 6, 7);
		assertThat(day(r, 3)).isEqualTo(new Row(3, null, null, LocalTime.of(11, 0), LocalTime.of(22, 0),
				LocalTime.of(21, 0), LocalTime.of(15, 0), LocalTime.of(17, 0)));
	}

	@Test
	void readsDayHeaders() {
		Result r = parse("[수요일~토요일]<br>- 11:30~20:00<br>- 준비시간 15:00~17:30<br>- 마지막 주문 19:10<br>[일요일]<br>- 11:30~15:00<br>- 마지막 주문 14:10",
				"매주 월요일, 화요일");
		assertThat(days(r)).containsExactly(3, 4, 5, 6, 7);
		assertThat(day(r, 7).close()).isEqualTo(LocalTime.of(15, 0));
		assertThat(day(r, 7).breakStart()).isNull();
		assertThat(day(r, 6).breakStart()).isEqualTo(LocalTime.of(15, 0));
	}

	@Test
	void readsDayRangeAcrossSunday() {
		Result r = parse("[일요일~목요일]10:30~22:00 (입장 마감 21:00)[금요일~토요일]10:30~23:00 (입장 마감 22:00)", "연중무휴");
		assertThat(days(r)).containsExactly(1, 2, 3, 4, 5, 6, 7);
		assertThat(day(r, 7).close()).isEqualTo(LocalTime.of(22, 0));
		assertThat(day(r, 5).close()).isEqualTo(LocalTime.of(23, 0));
	}

	@Test
	void readsSeasonsCoveringTheYear() {
		Result r = parse("- 하절기(3~10월) 10:00~17:00<br>\n- 동절기(11~2월) 10:00~16:00", "매주 월요일 / 1월 1일");
		assertThat(r.rows()).hasSize(12);
		assertThat(r.rows().getFirst()).isEqualTo(new Row(2, "03-01", "10-31", LocalTime.of(10, 0), LocalTime.of(17, 0), null, null, null));
		assertThat(r.rows().getLast()).isEqualTo(new Row(7, "11-01", "02-29", LocalTime.of(10, 0), LocalTime.of(16, 0), null, null, null));
	}

	@Test
	void readsLunchAndDinnerAsBreak() {
		Result r = parse("11:30~14:30, 17:30~22:00", "연중무휴");
		assertThat(day(r, 1)).isEqualTo(new Row(1, null, null, LocalTime.of(11, 30), LocalTime.of(22, 0), null,
				LocalTime.of(14, 30), LocalTime.of(17, 30)));
	}

	@Test
	void keepsPastMidnightClose() {
		assertThat(day(parse("18:00~익일 02:00", "연중무휴"), 1).close()).isEqualTo(LocalTime.of(2, 0));
		assertThat(day(parse("17:00~26:00", "연중무휴"), 1).close()).isEqualTo(LocalTime.of(2, 0));
	}

	@Test
	void readsAlwaysOpen() {
		assertThat(parse("상시 개방", "연중무휴").type()).isEqualTo(HoursType.ALWAYS_OPEN);
		assertThat(parse("상시 개방※ 계절별 탐방 권장시간이 상이하므로 홈페이지 참고", null).type()).isEqualTo(HoursType.ALWAYS_OPEN);
		assertThat(parse("상시 개방", "매주 월요일").type()).isEqualTo(HoursType.UNKNOWN);
	}

	@Test
	void treatsIrregularClosuresAsExceptions() {
		assertThat(days(parse("11:00~21:00", "매월 첫째·셋째 일요일"))).hasSize(7);
		assertThat(days(parse("11:00~21:00", "격주 일요일"))).hasSize(7);
		assertThat(days(parse("11:00~21:00", "매주 토요일~일요일 / 법정공휴일"))).containsExactly(1, 2, 3, 4, 5);
		assertThat(days(parse("11:00~21:00", "매주 일요일~월요일"))).containsExactly(2, 3, 4, 5, 6);
	}

	@Test
	void doesNotReadExceptedDaysAsClosed() {
		assertThat(days(parse("09:00~18:00", "매주 화요일 / 일요일을 제외한 관공서 공휴일(단, 중복 시 휴관) / 기타 도서관장이 정하는 날")))
				.containsExactly(1, 3, 4, 5, 6, 7);
		assertThat(days(parse("09:00~18:00", "매주 월요일 / 토요일, 일요일을 제외한 법정공휴일"))).containsExactly(2, 3, 4, 5, 6, 7);
		assertThat(days(parse("09:00~18:00", "매주 월요일 / 일요일과 공휴일이 겹치는 경우 휴관"))).containsExactly(2, 3, 4, 5, 6, 7);
		assertThat(days(parse("09:00~18:00", "매주 일요일과 공휴일"))).containsExactly(1, 2, 3, 4, 5, 6);
		assertThat(days(parse("10:00~22:00", "매주 월요일\n공휴일이 월요일인 경우 영업, 대체휴무 화요일"))).containsExactly(2, 3, 4, 5, 6, 7);
		assertThat(parse("09:00~18:00", "매주 월요일 / 매주 수요일 오후").reason()).isEqualTo("쉬는 날");
		assertThat(parse("10:00~17:00", "공휴일 / 동절기(12월~3월)").reason()).isEqualTo("쉬는 날");
		assertThat(parse("10:00~17:00", "매주 월요일 / 1월, 2월").reason()).isEqualTo("쉬는 날");
	}

	@Test
	void restRemovesDaysFromBroadDayWords() {
		assertThat(days(parse("- 평일 14:00~19:00\n- 주말 14:00~18:00", "매주 월요일~ 화요일"))).containsExactly(3, 4, 5, 6, 7);
		assertThat(parse("월요일~일요일 10:00~18:00", "매주 화요일").reason()).isEqualTo("휴무 요일에 운영시간");
	}

	@Test
	void readsRestaurantVariants() {
		Result lunchDinner = parse("- 11:00~21:00- 준비시간 15:00~17:00- 점심 마지막 주문 14:30 / 저녁 마지막 주문 20:30", "연중무휴");
		assertThat(day(lunchDinner, 1)).isEqualTo(new Row(1, null, null, LocalTime.of(11, 0), LocalTime.of(21, 0),
				LocalTime.of(20, 30), LocalTime.of(15, 0), LocalTime.of(17, 0)));
		Result twoOrders = parse("[평일]- 11:00~21:00- 준비시간 15:00~17:00- 마지막 주문 14:30, 20:30[토요일]- 12:00~16:00- 마지막 주문 15:30", "매주 일요일");
		assertThat(day(twoOrders, 1).lastEntry()).isEqualTo(LocalTime.of(20, 30));
		assertThat(day(twoOrders, 6).lastEntry()).isEqualTo(LocalTime.of(15, 30));
		Result clauses = parse("11:30~22:00(브레이크 타임 15:30~17:00, 라스트 오더 21:30)", "연중무휴");
		assertThat(day(clauses, 1)).isEqualTo(new Row(1, null, null, LocalTime.of(11, 30), LocalTime.of(22, 0),
				LocalTime.of(21, 30), LocalTime.of(15, 30), LocalTime.of(17, 0)));
		assertThat(day(parse("매일 11:30~22:00 (15:00~17:00 브레이크타임)", "연중무휴"), 1).breakStart()).isEqualTo(LocalTime.of(15, 0));
		Result glued = parse("주중 11:30~22:00주말 11:30~21:00", "명절 (전화문의)");
		assertThat(day(glued, 1).close()).isEqualTo(LocalTime.of(22, 0));
		assertThat(day(glued, 7).close()).isEqualTo(LocalTime.of(21, 0));
		Result slashInParens = parse("11:30~22:00 (브레이크타임 15:00~17:30 / 라스트오더 14:00, 21:00)", "연중무휴");
		assertThat(day(slashInParens, 1)).isEqualTo(new Row(1, null, null, LocalTime.of(11, 30), LocalTime.of(22, 0),
				LocalTime.of(21, 0), LocalTime.of(15, 0), LocalTime.of(17, 30)));
		Result gluedDay = parse("주중 11:30~22:30 (브레이크타임 15:00~17:00)토요일 11:30~22:00", "매주 일요일");
		assertThat(days(gluedDay)).containsExactly(1, 2, 3, 4, 5, 6);
		assertThat(day(gluedDay, 6).breakStart()).isNull();
		assertThat(day(parse("11:30~23:00 (22:00 라스트 오더)", "연중무휴"), 1).lastEntry()).isEqualTo(LocalTime.of(22, 0));
	}

	@Test
	void leavesPeriodNotesUnknown() {
		assertThat(parse("화요일~토요일 10:00~17:00\n※ 4월 1일~11월 30일에만 개관", "매주 일요일~월요일").reason()).isEqualTo("안내에 기간");
		assertThat(parse("10:00~18:00\n※ 1월 1일은 13:00부터 개관", "매주 월요일").type()).isEqualTo(HoursType.STRUCTURED);
	}

	@Test
	void needsRestTextExceptForFestivals() {
		assertThat(parse("09:00~18:00", null).reason()).isEqualTo("쉬는 날 없음");
		assertThat(OpeningHoursParser.parse("10:00~20:00", null, FESTIVAL).rows()).hasSize(7);
		assertThat(parse("매일 09:00~18:00", null).rows()).hasSize(7);
		assertThat(parse("평일 10:00~18:00 / 토요일 10:00~17:00", "매주 일요일").rows()).hasSize(6);
		assertThat(parse("평일 10:00~18:00 / 토요일 10:00~17:00", null).reason()).isEqualTo("요일이 빔");
	}

	@Test
	void leavesUnclearTextUnknown() {
		assertThat(parse("매장 별로 상이함<br>\n※ 홈페이지 참조", "매장 별로 상이함").type()).isEqualTo(HoursType.UNKNOWN);
		assertThat(parse("10:00 / 14:00<br>\n※ 사전 예약 필수", "연중무휴").type()).isEqualTo(HoursType.UNKNOWN);
		assertThat(parse("[사찰음식점] 12:00~15:00<br>※ 예약제로 운영<br>[베이커리] 10:00~20:00", "연중무휴").type()).isEqualTo(HoursType.UNKNOWN);
		assertThat(parse("- 입실 13:00- 퇴실 11:00", "연중무휴").type()).isEqualTo(HoursType.UNKNOWN);
		assertThat(parse("- 하절기(3월~10월) 10:00~19:00<br>\n- 동절기(11월~2월) 110:00~18:00<br>\n- 주말 10:00~17:00", "매주 월요일").type())
				.isEqualTo(HoursType.UNKNOWN);
		assertThat(parse("[6월~9월] 10:00~11:30[1월~5월 / 10월~12월] 14:00~15:30", "연중무휴").type()).isEqualTo(HoursType.UNKNOWN);
		assertThat(parse("09:00~18:00", "점포 별로 상이함").reason()).isEqualTo("쉬는 날");
		assertThat(parse("09:00~18:00", "※ 자세한 사항은 전화문의 요망").reason()).isEqualTo("쉬는 날");
		assertThat(parse(null, "연중무휴").reason()).isEqualTo("운영시간 없음");
	}
}
