package com.tukorea.gdgoc.koready.domain.place;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.tukorea.gdgoc.koready.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 숙소 검색. 로그인 없이 부르고, 결과는 덤프 데이터(숙박 382곳) 기준이다.
 */
@IntegrationTest
class LodgingTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	/** 이름에 검색어가 들어간 숙소가 먼저(가나다순), 그다음 주소에만 들어간 숙소다 */
	@Test
	void namesComeBeforeAddresses() {
		MvcTestResult result = lodgings("query", "명동");

		assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"page": 0, "size": 20, "totalCount": 17, "hasNext": false}
				""");
		assertThat(result).bodyJson().extractingPath("$.items[*].nameKo").isEqualTo(List.of(
				"57명동호스텔", "L7 명동 바이 롯데호텔", "나인트리 바이 파르나스 서울 명동", "롯데시티호텔 명동",
				"르메르디앙 서울 명동", "명동게스트하우스 꼬모", "목시 서울 명동", "보코 서울 명동",
				"소테츠 프레사 인 서울 명동", "소테츠 호텔즈 더 스프라지르 서울 명동", "이비스 앰배서더 명동",
				"코트야드 메리어트 서울 명동", "포포인츠 바이 쉐라톤 조선 서울 명동", "호텔 스카이파크 센트럴 명동점",
				"호텔토마스명동",
				// 주소에만 있다 (명동길 61, 명동9가길 14)
				"로얄호텔서울", "메트로호텔"));
	}

	/** 이름이 정확히 맞는 숙소가 먼저다 (영문은 대소문자 무시). 가나다순이면 영문 이름이 앞선다 */
	@Test
	void exactNameComesFirst() {
		assertThat(lodgings("query", "정스패밀리")).bodyJson().extractingPath("$.items[*].nameKo")
				.isEqualTo(List.of("정스패밀리", "Jeong's family(정스패밀리)"));
		assertThat(lodgings("query", "y's house")).bodyJson().extractingPath("$.items[*].nameKo")
				.isEqualTo(List.of("Y's house", "Goldlady's House"));
	}

	@Test
	void itemShowsAddressTypeAndLocation() {
		Map<String, Object> place = jdbc.queryForMap("""
				SELECT id, ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lng
				FROM places WHERE name_ko = '남산호스텔'
				""");

		MvcTestResult result = lodgings("query", "남산호스텔");

		assertThat(result).bodyJson().isLenientlyEqualTo("""
				{"totalCount": 1,
				 "items": [{"placeId": %d, "name": "남산호스텔", "nameKo": "남산호스텔",
				            "address": "서울특별시 중구 퇴계로18길 67 (남산동2가)",
				            "lodgingType": {"code": "AC060200", "name": "게스트하우스"}}]}
				""".formatted((Long) place.get("id")));
		assertThat(result).bodyJson().extractingPath("$.items[0].location")
				.isEqualTo(Map.of("lat", place.get("lat"), "lng", place.get("lng")));
	}

	/** 상세 주소(addr2)에만 있는 동 이름으로도 찾는다. 주소는 응답처럼 addr1·addr2를 이어 붙인 문자열에서 찾는다 */
	@Test
	void searchesDetailAddress() {
		assertThat(lodgings("query", "서교동")).bodyJson().extractingPath("$.items[*].nameKo").asArray()
				.hasSize(10)
				.contains("나루게스트하우스", "니트하우스", "어반우드", "헤이마 게스트하우스");
		assertThat(lodgings("query", "67 (남산동2가)")).bodyJson().extractingPath("$.items[*].nameKo")
				.isEqualTo(List.of("남산호스텔"));
	}

	/** 영문명이 비어 있어 영어로 요청해도 이름·유형은 국문이다. 영문 검색어는 국문명 속 영문을 대소문자 없이 찾는다 */
	@Test
	void englishRequestFallsBackToKorean() {
		assertThat(mvc.get().uri("/api/lodgings").param("query", "GANGNAM stay hill").header("Accept-Language", "en"))
				.hasStatusOk()
				.bodyJson()
				.isLenientlyEqualTo("""
						{"totalCount": 1,
						 "items": [{"name": "강남스테이힐(Gangnam Stay Hill)", "nameKo": "강남스테이힐(Gangnam Stay Hill)",
						            "address": "서울특별시 강남구 테헤란로13길 65 (역삼동)1, 2층",
						            "lodgingType": {"code": "AC060200", "name": "게스트하우스"}}]}
						""");
	}

	/** 숙박(콘텐츠 유형 32)만 찾는다. 대분류가 숙박인 레포츠 유형 캠핑장, 사라진 숙소, 제외 분류의 숙소는 빠진다 */
	@Test
	void findsOnlySelectableLodgings() {
		assertThat(lodgings("query", "캠핑장")).hasStatusOk().bodyJson().extractingPath("$.totalCount").isEqualTo(0);

		jdbc.update("UPDATE places SET active = false WHERE name_ko = '남산호스텔'");
		assertThat(lodgings("query", "남산호스텔")).bodyJson().extractingPath("$.totalCount").isEqualTo(0);

		jdbc.update("UPDATE place_classes SET excluded = true WHERE code = 'AC06'");
		assertThat(lodgings("query", "정스패밀리")).bodyJson().extractingPath("$.totalCount").isEqualTo(0);
	}

	@Test
	void pagesContinueInSameOrder() {
		byte[] firstTenBody = lodgings("query", "호텔", "size", "10").getResponse().getContentAsByteArray();
		List<Integer> firstTen = JsonPath.read(new String(firstTenBody, StandardCharsets.UTF_8), "$.items[*].placeId");

		MvcTestResult secondPage = lodgings("query", "호텔", "page", "1", "size", "5");

		assertThat(secondPage).bodyJson().isLenientlyEqualTo("""
				{"page": 1, "size": 5, "totalCount": 118, "hasNext": true}
				""");
		assertThat(secondPage).bodyJson().extractingPath("$.items[*].placeId").isEqualTo(firstTen.subList(5, 10));
		MvcTestResult lastPage = lodgings("query", "호텔", "page", "5");
		assertThat(lastPage).bodyJson().isLenientlyEqualTo("""
				{"page": 5, "size": 20, "totalCount": 118, "hasNext": false}
				""");
		assertThat(lastPage).bodyJson().extractingPath("$.items.length()").isEqualTo(18);
	}

	/** 검색어 길이는 앞뒤 공백을 뺀 글자 수로 센다. %·_는 와일드카드가 아니라 글자 그대로 찾는다 */
	@Test
	void acceptsPaddedQueryAndTreatsWildcardsAsText() {
		assertThat(lodgings("query", " " + "가".repeat(50) + " ")).hasStatusOk();
		for (String wildcard : List.of("%", "_")) {
			assertThat(lodgings("query", wildcard)).hasStatusOk().bodyJson()
					.extractingPath("$.totalCount").isEqualTo(0);
		}
	}

	@Test
	void rejectsBadQueryAndPage() {
		assertThat(mvc.get().uri("/api/lodgings"))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "query", "reason": "필수입니다"}]}
						""");
		// 전각 공백(U+3000)도 공백이다
		for (String blank : List.of("", "   ", "　")) {
			assertThat(lodgings("query", blank))
					.hasStatus(HttpStatus.BAD_REQUEST)
					.bodyJson()
					.isLenientlyEqualTo("""
							{"code": "INVALID_REQUEST", "fields": [{"field": "query", "reason": "공백만으로는 찾을 수 없습니다"}]}
							""");
		}
		assertThat(lodgings("query", "가".repeat(51)))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.isLenientlyEqualTo("""
						{"code": "INVALID_REQUEST", "fields": [{"field": "query", "reason": "50자 이하로 찾을 수 있습니다"}]}
						""");
		for (String[] params : List.of(
				new String[] {"size", "51"},
				new String[] {"page", "-1"},
				new String[] {"page", "abc"})) {
			assertThat(lodgings("query", "호텔", params[0], params[1]))
					.hasStatus(HttpStatus.BAD_REQUEST)
					.bodyJson()
					.extractingPath("$.fields[0].field").isEqualTo(params[0]);
		}
	}

	/** 숙소 검색을 부른다. params는 이름·값을 번갈아 둔다 */
	private MvcTestResult lodgings(String... params) {
		var request = mvc.get().uri("/api/lodgings");
		for (int i = 0; i < params.length; i += 2) {
			request = request.param(params[i], params[i + 1]);
		}
		return request.exchange();
	}
}
