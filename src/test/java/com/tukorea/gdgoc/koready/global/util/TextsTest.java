package com.tukorea.gdgoc.koready.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextsTest {

	@Test
	void 길이가_한도_이하면_그대로_둔다() {
		assertThat(Texts.truncate(null, 3)).isNull();
		assertThat(Texts.truncate("", 3)).isEmpty();
		assertThat(Texts.truncate("가나다", 3)).isEqualTo("가나다");
	}

	@Test
	void 한도를_넘으면_글자_수로_자른다() {
		assertThat(Texts.truncate("가나다라", 3)).isEqualTo("가나다");
	}

	@Test
	void UTF16_두_칸인_글자는_한_글자로_세고_반으로_가르지_않는다() {
		String smile = "😀";
		assertThat(Texts.truncate("가나" + smile, 3)).isEqualTo("가나" + smile);
		assertThat(Texts.truncate("가나" + smile + "라", 3)).isEqualTo("가나" + smile);
		assertThat(Texts.truncate(smile + smile, 1)).isEqualTo(smile);
	}
}
