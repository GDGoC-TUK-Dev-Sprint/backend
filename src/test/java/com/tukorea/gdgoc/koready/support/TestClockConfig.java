package com.tukorea.gdgoc.koready.support;

import com.tukorea.gdgoc.koready.common.SeoulTime;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트의 "오늘"을 서울 2026-11-01 10:00으로 고정한다.
 */
@TestConfiguration
public class TestClockConfig {

	public static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

	@Bean
	@Primary
	Clock fixedClock() {
		return Clock.fixed(TODAY.atTime(10, 0).atZone(SeoulTime.ZONE).toInstant(), SeoulTime.ZONE);
	}
}
