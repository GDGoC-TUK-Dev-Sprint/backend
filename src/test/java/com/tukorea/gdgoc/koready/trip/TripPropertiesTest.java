package com.tukorea.gdgoc.koready.trip;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 여행 조건 설정값. DB 없이 설정 바인딩만 띄운다.
 */
class TripPropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(PropertiesConfig.class);

	@Test
	void bindsSettings() {
		runner.withPropertyValues("koready.trip.max-days=10", "koready.trip.max-party-size=8")
				.run(context -> assertThat(context.getBean(TripProperties.class)).isEqualTo(new TripProperties(10, 8)));
	}

	@Test
	void missingSettingStopsStartup() {
		runner.withPropertyValues("koready.trip.max-days=10")
				.run(context -> assertThat(context).getFailure().hasRootCauseInstanceOf(BindValidationException.class));
	}

	@EnableConfigurationProperties(TripProperties.class)
	static class PropertiesConfig {
	}
}
