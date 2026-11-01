package com.tukorea.gdgoc.koready.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 장소 후보 설정값. DB 없이 설정 바인딩만 띄운다.
 */
class CandidatePropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(PropertiesConfig.class);

	@Test
	void bindsSettings() {
		runner.withPropertyValues("koready.candidate.signal-weight-m=3000", "koready.candidate.near-stay-m=1000",
						"koready.candidate.recommended-min-per-day=3", "koready.candidate.recommended-max-per-day=5",
						"koready.candidate.max-per-day=6")
				.run(context -> assertThat(context.getBean(CandidateProperties.class))
						.isEqualTo(new CandidateProperties(3000, 1000, 3, 5, 6)));
	}

	@Test
	void missingSettingStopsStartup() {
		runner.withPropertyValues("koready.candidate.near-stay-m=1000")
				.run(context -> assertThat(context).getFailure().hasRootCauseInstanceOf(BindValidationException.class));
	}

	@EnableConfigurationProperties(CandidateProperties.class)
	static class PropertiesConfig {
	}
}
