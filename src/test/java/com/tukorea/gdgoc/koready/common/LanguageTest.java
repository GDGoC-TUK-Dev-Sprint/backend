package com.tukorea.gdgoc.koready.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LanguageTest {

	@Test
	void picksKoreanUnlessEnglishIsPreferred() {
		assertThat(Language.from(null)).isEqualTo(Language.KO);
		assertThat(Language.from("")).isEqualTo(Language.KO);
		assertThat(Language.from("ko")).isEqualTo(Language.KO);
		assertThat(Language.from("en")).isEqualTo(Language.EN);
		assertThat(Language.from("en-US,en;q=0.9")).isEqualTo(Language.EN);
		assertThat(Language.from("ko-KR,en;q=0.5")).isEqualTo(Language.KO);
		assertThat(Language.from("ja")).isEqualTo(Language.KO);
		assertThat(Language.from(";;;")).isEqualTo(Language.KO);
	}

	@Test
	void fallsBackToKoreanText() {
		assertThat(Language.EN.pick("경복궁", "Gyeongbokgung Palace")).isEqualTo("Gyeongbokgung Palace");
		assertThat(Language.EN.pick("경복궁", null)).isEqualTo("경복궁");
		assertThat(Language.EN.pick("경복궁", " ")).isEqualTo("경복궁");
		assertThat(Language.KO.pick("경복궁", "Gyeongbokgung Palace")).isEqualTo("경복궁");
	}
}
