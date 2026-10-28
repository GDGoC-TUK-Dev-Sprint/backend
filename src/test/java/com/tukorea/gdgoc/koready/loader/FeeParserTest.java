package com.tukorea.gdgoc.koready.loader;

import static org.assertj.core.api.Assertions.assertThat;

import com.tukorea.gdgoc.koready.loader.FeeParser.Fee;
import com.tukorea.gdgoc.koready.place.FeeAudience;
import com.tukorea.gdgoc.koready.place.FeeType;
import org.junit.jupiter.api.Test;

class FeeParserTest {

	private static FeeType type(String text) {
		return FeeParser.parse(text).type();
	}

	@Test
	void readsFree() {
		assertThat(type("무료 ")).isEqualTo(FeeType.FREE);
		assertThat(type("무료<br>\n※ 대여료는 별도 상이하므로 전화문의 요망")).isEqualTo(FeeType.FREE);
		assertThat(type("무료 (부분별 유료)")).isEqualTo(FeeType.FREE);
		assertThat(type("상설전시 무료입장<br>\n* 기획전시는 홈페이지참고")).isEqualTo(FeeType.FREE);
		assertThat(FeeParser.parse("무료").fees()).isEmpty();
	}

	@Test
	void readsAudienceAmounts() {
		assertThat(FeeParser.parse("- 개인 1,000원\n- 단체 800원").fees()).containsExactly(
				new Fee(FeeAudience.ADULT, "개인", 1000), new Fee(FeeAudience.OTHER, "단체", 800));
		assertThat(FeeParser.parse("- 어른 31,000원- 어린이 27,000원※ 자세한 사항은 전화문의 요망").fees()).containsExactly(
				new Fee(FeeAudience.ADULT, "어른", 31000), new Fee(FeeAudience.CHILD, "어린이", 27000));
		assertThat(FeeParser.parse("- 성인 3,000원- 청소년 / 65세 이상 2,000원- 국가유공자 / 장애인 1,500원※ 무료 : 미취학 아동").fees())
				.containsExactly(new Fee(FeeAudience.ADULT, "성인", 3000), new Fee(FeeAudience.YOUTH, "청소년", 2000),
						new Fee(FeeAudience.SENIOR, "65세 이상", 2000), new Fee(FeeAudience.OTHER, "국가유공자", 1500),
						new Fee(FeeAudience.OTHER, "장애인", 1500));
		assertThat(FeeParser.parse("- 개인 대인 3,000원\n- 개인 소인 1,500원").fees()).containsExactly(
				new Fee(FeeAudience.ADULT, "개인 대인", 3000), new Fee(FeeAudience.CHILD, "개인 소인", 1500));
	}

	@Test
	void usesDefaultLabelOrHeader() {
		assertThat(FeeParser.parse("13,000원<br>\n※ 무료 : 장애인 / 24개월 미만").fees())
				.containsExactly(new Fee(FeeAudience.ADULT, "입장료", 13000));
		assertThat(FeeParser.parse("일반요금 5,000원<br>※ 무료 : 소외계층").fees())
				.containsExactly(new Fee(FeeAudience.ADULT, "일반요금", 5000));
		assertThat(FeeParser.parse("[대인(만25세~64세)/외국인(만19세~64세)]\n- 3,000원\n※ 무료 : 청소년/경로/장애인").fees()).containsExactly(
				new Fee(FeeAudience.ADULT, "대인(만25세~64세)", 3000), new Fee(FeeAudience.OTHER, "외국인(만19세~64세)", 3000));
	}

	@Test
	void keepsGroupPricesOutOfAdult() {
		var result = FeeParser.parse("[개인]- 대인 5,000원- 소인 3,000원[단체(10인 이상)]- 대인 4,000원- 소인 2,000원※ 자세한 사항은 홈페이지 참조");
		assertThat(result.type()).isEqualTo(FeeType.PAID);
		assertThat(result.fees()).filteredOn(f -> f.audience() == FeeAudience.ADULT)
				.containsExactly(new Fee(FeeAudience.ADULT, "개인 대인", 5000));
		assertThat(result.fees()).contains(new Fee(FeeAudience.OTHER, "단체(10인 이상) 대인", 4000));
	}

	@Test
	void readsAgeLabels() {
		assertThat(FeeParser.parse("[일반]\n- 19세 이상 5,000원\n- 8~18세 3,000원\n- 36개월~7세 2,000원").fees()).containsExactly(
				new Fee(FeeAudience.ADULT, "일반 19세 이상", 5000), new Fee(FeeAudience.YOUTH, "일반 8~18세", 3000),
				new Fee(FeeAudience.CHILD, "일반 36개월~7세", 2000));
	}

	@Test
	void readsLooserForms() {
		assertThat(FeeParser.parse("유료(1인 35,000원)").fees()).containsExactly(new Fee(FeeAudience.ADULT, "1인", 35000));
		assertThat(FeeParser.parse("유료 (30,000원)").fees()).containsExactly(new Fee(FeeAudience.ADULT, "입장료", 30000));
		assertThat(FeeParser.parse("- 성인 12,000원  \n- 청소년(13세~18세) 6,000\n※ 무료 : 36개월~12세").fees()).containsExactly(
				new Fee(FeeAudience.ADULT, "성인", 12000), new Fee(FeeAudience.YOUTH, "청소년(13세~18세)", 6000));
		assertThat(FeeParser.parse("[개인]\n- 일반 4,000원\n- 청소년, 대학생 3,000원").fees()).containsExactly(
				new Fee(FeeAudience.ADULT, "개인 일반", 4000), new Fee(FeeAudience.YOUTH, "개인 청소년", 3000),
				new Fee(FeeAudience.OTHER, "대학생", 3000));
	}

	@Test
	void skipsFreeListsAndDiscounts() {
		assertThat(FeeParser.parse("[개인]\n- 어른 1,000원\n- 학생(만 7세~만 18세)/군인 500원\n[단체 (20인 이상)]\n- 어른 700원\n※ 무료\n- 만 6세 미만 / 만 65세 이상 / 국가유공자")
				.fees()).filteredOn(f -> f.audience() == FeeAudience.ADULT).containsExactly(new Fee(FeeAudience.ADULT, "개인 어른", 1000));
		assertThat(FeeParser.parse("- 성인 1,000원- 청소년 및 군경 500원 [무료 관람의 날]  - 매월 2·4주 토요일 / 설·추석의 전·후일 ※ 무료관람객 등 자세한 사항은 홈페이지 참조")
				.type()).isEqualTo(FeeType.PAID);
		assertThat(FeeParser.parse("- 일반 65,000원- 장애인 50% 할인※ 무료 : 24개월 미만").fees())
				.containsExactly(new Fee(FeeAudience.ADULT, "일반", 65000));
	}

	@Test
	void marksVaryingPrices() {
		assertThat(type("부분 유료")).isEqualTo(FeeType.VARIES);
		assertThat(type("- 교육장 17,500원- 세미나실 5,500원※ 자세한 사항은 홈페이지 참조")).isEqualTo(FeeType.VARIES);
		assertThat(type("- 평일 4,000원- 주말, 공휴일 5,000원")).isEqualTo(FeeType.VARIES);
		assertThat(type("- VIP석 70,000원- S석 60,000원- A석 50,000원")).isEqualTo(FeeType.VARIES);
		assertThat(type("[전각관람] 3,000원<br>\n[후원관람]<br>\n- 대인 5,000원<br>\n- 소인 2,500원")).isEqualTo(FeeType.VARIES);
		assertThat(type("공연 별로 상이함")).isEqualTo(FeeType.VARIES);
		assertThat(type("※ 공연 별 상이하므로 홈페이지 참조 요망")).isEqualTo(FeeType.VARIES);
		assertThat(type("유료(궁궐 별 입장료 상이)")).isEqualTo(FeeType.VARIES);
		assertThat(type("공연 무료 / 프리마켓, 푸드트럭, 체험부스 유료")).isEqualTo(FeeType.VARIES);
	}

	@Test
	void leavesUnclearTextUnknown() {
		assertThat(type(null)).isEqualTo(FeeType.UNKNOWN);
		assertThat(type("유료")).isEqualTo(FeeType.UNKNOWN);
		assertThat(type("입장료 유료 (1인 2,000원) / 성북구민 무료")).isEqualTo(FeeType.UNKNOWN);
		assertThat(type("※ 자세한 사항은 홈페이지 참조")).isEqualTo(FeeType.UNKNOWN);
	}
}
