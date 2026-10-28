package com.tukorea.gdgoc.koready.loader;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.place.StayBasis;
import org.junit.jupiter.api.Test;

class StayMinutesTest {

	@Test
	void parsesSingleDuration() {
		assertThat(StayMinutes.parse("약 1시간")).isEqualTo(60);
		assertThat(StayMinutes.parse("약 1시간 30분")).isEqualTo(90);
		assertThat(StayMinutes.parse("75분")).isEqualTo(75);
		assertThat(StayMinutes.parse("60분 내외")).isEqualTo(60);
		assertThat(StayMinutes.parse("2시간")).isEqualTo(120);
	}

	@Test
	void ignoresRangesAndSentences() {
		assertThat(StayMinutes.parse("1∼2시간 정도")).isNull();
		assertThat(StayMinutes.parse("약 30분~1시간")).isNull();
		assertThat(StayMinutes.parse("공연 별로 상이함")).isNull();
		assertThat(StayMinutes.parse("월~금 09:00 ~ 22:00 / 토~일 09:00 ~ 18:00")).isNull();
		assertThat(StayMinutes.parse("약")).isNull();
		assertThat(StayMinutes.parse(null)).isNull();
	}

	@Test
	void fallsBackToClassThenDefault() {
		assertThat(StayMinutes.of("VE", "VE07", "약 2시간")).isEqualTo(new StayMinutes.Result(120, StayBasis.TOURAPI));
		assertThat(StayMinutes.of("VE", "VE06", "공연별로 상이함")).isEqualTo(new StayMinutes.Result(120, StayBasis.CLASS));
		assertThat(StayMinutes.of("SH", "SH04", null)).isEqualTo(new StayMinutes.Result(60, StayBasis.CLASS));
		assertThat(StayMinutes.of("AC", "AC01", null)).isEqualTo(new StayMinutes.Result(0, StayBasis.CLASS));
	}
}
