package com.tukorea.gdgoc.koready.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "오늘"·"지금"은 이 Clock(서울)으로 정한다: {@code LocalDate.now(clock)}. 테스트에서 날짜를 고정할 수 있다.
 */
@Configuration
class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.system(SeoulTime.ZONE);
	}
}
