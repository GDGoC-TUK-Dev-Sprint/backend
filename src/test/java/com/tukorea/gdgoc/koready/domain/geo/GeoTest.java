package com.tukorea.gdgoc.koready.domain.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import com.tukorea.gdgoc.koready.infra.naver.GeocodeAddress;
import com.tukorea.gdgoc.koready.infra.naver.NaverMapsClient;
import com.tukorea.gdgoc.koready.infra.naver.ReverseGeocodeResult;
import com.tukorea.gdgoc.koready.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 지도에서 숙소 위치 선택의 주소·장소명 검색과 핀 위치 주소. 로그인 없이 부른다.
 * 네이버 클라이언트는 가짜이고(호출·응답 읽기는 NaverMapsClientTest), 장소 결과는 덤프 데이터 기준이다.
 */
@IntegrationTest
class GeoTest {

	private static final GeocodeAddress MYEONGDONG = seoul("서울특별시 중구 명동", "서울특별시 중구 명동",
			"Myeong-dong, Jung-gu, Seoul, Republic of Korea", 37.5609, 126.9863);
	private static final GeocodeAddress CHUNCHEON = new GeocodeAddress("강원특별자치도 춘천시 명동길 14",
			"강원특별자치도 춘천시 중앙로2가 23-1", "14, Myeongdong-gil, Chuncheon-si, Gangwon-do, Republic of Korea",
			"강원특별자치도", 37.8793856, 127.7270855);

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	NaverMapsClient naverMaps;

	/** 주소 결과(서울 먼저, 최대 5개) 다음에 장소 결과(최대 5개)다. 장소는 이름이 정확히 맞는 것 → 앞부분이 맞는 것 중 신호가 많은 순 */
	@Test
	void addressesThenPlaces() {
		given(naverMaps.geocode("명동", 20)).willReturn(List.of(
				CHUNCHEON,
				MYEONGDONG,
				seoul("서울특별시 중구 명동길 14 Noon Square", null, null, 37.5634327, 126.9828804),
				seoul(null, "서울특별시 중구 명동2가 83-5", null, 37.5634, 126.9828),
				seoul("서울특별시 중구 명동8길 10", null, null, 37.5612, 126.9851),
				seoul("서울특별시 중구 명동10길 19", null, null, 37.5620, 126.9858)));

		MvcTestResult result = search("명동");

		assertThat(result).hasStatusOk().bodyJson().extractingPath("$.items[*].type").isEqualTo(List.of(
				"ADDRESS", "ADDRESS", "ADDRESS", "ADDRESS", "ADDRESS", "PLACE", "PLACE", "PLACE", "PLACE", "PLACE"));
		assertThat(result).bodyJson().extractingPath("$.items[*].label").isEqualTo(List.of(
				// 서울 주소가 네이버 순서대로 먼저 오고, 서울 밖(춘천)은 6번째로 밀려 빠진다. 도로명이 없으면 지번
				"서울특별시 중구 명동", "서울특별시 중구 명동길 14 Noon Square", "서울특별시 중구 명동2가 83-5",
				"서울특별시 중구 명동8길 10", "서울특별시 중구 명동10길 19",
				// 신호 0인 "명동 영양센터"는 가나다순으로 앞서도 신호 1인 곳 뒤다
				"명동", "명동 남대문 북창동 다동무교동 관광특구", "명동교자", "명동돈가스", "명동쭈꾸미"));
		assertThat(result).bodyJson().extractingPath("$.items[*].address").isEqualTo(List.of(
				"서울특별시 중구 명동", "서울특별시 중구 명동길 14 Noon Square", "서울특별시 중구 명동2가 83-5",
				"서울특별시 중구 명동8길 10", "서울특별시 중구 명동10길 19",
				"서울특별시 중구 명동2가", "서울특별시 중구 세종대로 40 (남대문로4가)", "서울특별시 중구 퇴계로 129 (충무로2가)",
				"서울특별시 중구 명동3길 8 (명동1가)", "서울특별시 중구 퇴계로6길 3-28 (회현동1가)"));
		assertThat(result).bodyJson().extractingPath("$.items[*].inSeoul").asArray().hasSize(10).containsOnly(true);
		assertThat(result).bodyJson().extractingPath("$.items[0].location")
				.isEqualTo(Map.of("lat", 37.5609, "lng", 126.9863));
		Map<String, Object> place = jdbc.queryForMap("""
				SELECT ST_Y(location::geometry) AS lat, ST_X(location::geometry) AS lng FROM places WHERE content_id = '264311'
				""");
		assertThat(result).bodyJson().extractingPath("$.items[5].location")
				.isEqualTo(Map.of("lat", place.get("lat"), "lng", place.get("lng")));
	}

	/** 서울 밖 주소는 서울 주소 뒤에 inSeoul = false로 온다. 주소가 없는 결과는 뺀다 */
	@Test
	void addressOutsideSeoul() {
		given(naverMaps.geocode("명동길 14", 20)).willReturn(List.of(
				CHUNCHEON,
				new GeocodeAddress(null, null, null, "서울특별시", 37.56, 126.98),
				seoul("서울특별시 중구 명동길 14 Noon Square", null, null, 37.5634327, 126.9828804)));

		assertThat(search("명동길 14")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"items": [
				  {"type": "ADDRESS", "label": "서울특별시 중구 명동길 14 Noon Square",
				   "address": "서울특별시 중구 명동길 14 Noon Square",
				   "location": {"lat": 37.5634327, "lng": 126.9828804}, "inSeoul": true},
				  {"type": "ADDRESS", "label": "강원특별자치도 춘천시 명동길 14", "address": "강원특별자치도 춘천시 명동길 14",
				   "location": {"lat": 37.8793856, "lng": 127.7270855}, "inSeoul": false}]}
				""");
	}

	/** 영어로 요청하면 주소 결과는 영문 주소(없으면 국문)다. 장소는 영문명이 비어 있어 국문이다 */
	@Test
	void englishRequest() {
		given(naverMaps.geocode("명동", 20)).willReturn(List.of(
				MYEONGDONG, seoul("서울특별시 중구 명동길 14 Noon Square", null, null, 37.5634327, 126.9828804)));

		MvcTestResult result = mvc.get().uri("/api/geo/search").param("query", "명동").header("Accept-Language", "en")
				.exchange();

		assertThat(result).hasStatusOk().bodyJson().extractingPath("$.items[*].label").isEqualTo(List.of(
				"Myeong-dong, Jung-gu, Seoul, Republic of Korea", "서울특별시 중구 명동길 14 Noon Square",
				"명동", "명동 남대문 북창동 다동무교동 관광특구", "명동교자", "명동돈가스", "명동쭈꾸미"));
		assertThat(result).bodyJson().extractingPath("$.items[0].address")
				.isEqualTo("Myeong-dong, Jung-gu, Seoul, Republic of Korea");
		assertThat(result).bodyJson().extractingPath("$.items[2].address").isEqualTo("서울특별시 중구 명동2가");
	}

	/** 이름 앞부분이 맞는 곳이 신호가 더 많은 곳보다 먼저다. 숙박도 찾고, 사라진 장소·제외 분류는 뺀다 */
	@Test
	void placeResults() {
		given(naverMaps.geocode(anyString(), anyInt())).willReturn(List.of());

		assertThat(search("경복궁")).hasStatusOk().bodyJson().extractingPath("$.items[*].label").isEqualTo(List.of(
				"경복궁", "경복궁 별빛야행", "경복궁 생과방", "경복궁에 맛있는 부엌",
				// 신호 1이지만 이름 앞부분이 맞지 않는다
				"한복남 경복궁점"));
		assertThat(search("남산호스텔")).bodyJson().extractingPath("$.items[*].label")
				.isEqualTo(List.of("남산호스텔"));

		jdbc.update("UPDATE places SET active = false WHERE name_ko = '남산호스텔'");
		assertThat(search("남산호스텔")).bodyJson().extractingPath("$.items").asArray().isEmpty();

		jdbc.update("UPDATE place_classes SET excluded = true WHERE code = 'EV'");
		assertThat(search("경복궁")).bodyJson().extractingPath("$.items[*].label")
				.isEqualTo(List.of("경복궁", "경복궁에 맛있는 부엌", "한복남 경복궁점"));
	}

	/** 검색어는 앞뒤 공백을 빼고 2~100자다. 검사에 걸리면 네이버를 부르지 않는다 */
	@Test
	void invalidQuery() {
		assertThat(search(" 명 ")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isLenientlyEqualTo("""
				{"code": "INVALID_REQUEST", "fields": [{"field": "query", "reason": "2자 이상으로 찾을 수 있습니다"}]}
				""");
		assertThat(search("　 ")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isLenientlyEqualTo("""
				{"fields": [{"field": "query", "reason": "공백만으로는 찾을 수 없습니다"}]}
				""");
		assertThat(search("가".repeat(101))).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isLenientlyEqualTo("""
				{"fields": [{"field": "query", "reason": "100자 이하로 찾을 수 있습니다"}]}
				""");
		assertThat(mvc.get().uri("/api/geo/search")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isLenientlyEqualTo("""
				{"fields": [{"field": "query", "reason": "필수입니다"}]}
				""");
		verifyNoInteractions(naverMaps);
	}

	@Test
	void searchNaverFailure() {
		given(naverMaps.geocode("명동", 20)).willThrow(new ApiException(ErrorCode.EXTERNAL_API_FAILED));

		assertThat(search("명동")).hasStatus(HttpStatus.BAD_GATEWAY).bodyJson().isLenientlyEqualTo("""
				{"code": "EXTERNAL_API_FAILED"}
				""");
	}

	/** 도로명 주소, 없으면 지번 주소다. 서울은 법정동 코드 앞 두 자리가 11이고, 결과가 없으면(바다·국외) 주소 null */
	@Test
	void reverse() {
		given(naverMaps.reverseGeocode(37.5637, 126.9849)).willReturn(new ReverseGeocodeResult(
				"1114012700", "서울특별시 중구 명동길 48 청휘빌딩", "서울특별시 중구 명동2가 33-1"));
		given(naverMaps.reverseGeocode(37.5, 127.0)).willReturn(
				new ReverseGeocodeResult("1165010800", null, "서울특별시 서초구 반포동 1-1"));
		given(naverMaps.reverseGeocode(37.4, 127.1)).willReturn(new ReverseGeocodeResult(
				"4113510900", "경기도 성남시 분당구 경부고속도로 409", "경기도 성남시 분당구 삼평동 421"));
		given(naverMaps.reverseGeocode(35.68, 139.76)).willReturn(new ReverseGeocodeResult(null, null, null));

		assertThat(reverse("37.5637", "126.9849")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"address": "서울특별시 중구 명동길 48 청휘빌딩", "inSeoul": true}
				""");
		assertThat(reverse("37.5", "127.0")).bodyJson().isStrictlyEqualTo("""
				{"address": "서울특별시 서초구 반포동 1-1", "inSeoul": true}
				""");
		assertThat(reverse("37.4", "127.1")).bodyJson().isStrictlyEqualTo("""
				{"address": "경기도 성남시 분당구 경부고속도로 409", "inSeoul": false}
				""");
		assertThat(reverse("35.68", "139.76")).bodyJson().isStrictlyEqualTo("""
				{"address": null, "inSeoul": false}
				""");
	}

	/** 위도 -90~90, 경도 -180~180. 검사에 걸리면 네이버를 부르지 않는다 */
	@Test
	void invalidCoordinates() {
		assertThat(reverse("90.1", "126.9")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isLenientlyEqualTo("""
				{"code": "INVALID_REQUEST", "fields": [{"field": "lat"}]}
				""");
		// NaN은 최솟값·최댓값 검사에 모두 걸린다
		assertThat(reverse("37.5", "NaN")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.fields[*].field")
				.isEqualTo(List.of("lng", "lng"));
		assertThat(reverse("north", "126.9")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isLenientlyEqualTo("""
				{"fields": [{"field": "lat", "reason": "형식이 맞지 않습니다"}]}
				""");
		assertThat(mvc.get().uri("/api/geo/reverse").param("lat", "37.5")).hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().isLenientlyEqualTo("""
						{"fields": [{"field": "lng", "reason": "필수입니다"}]}
						""");
		verifyNoInteractions(naverMaps);
	}

	@Test
	void reverseNaverFailure() {
		given(naverMaps.reverseGeocode(anyDouble(), anyDouble()))
				.willThrow(new ApiException(ErrorCode.EXTERNAL_API_FAILED));

		assertThat(reverse("37.5637", "126.9849")).hasStatus(HttpStatus.BAD_GATEWAY).bodyJson()
				.isLenientlyEqualTo("""
						{"code": "EXTERNAL_API_FAILED"}
						""");
	}

	private MvcTestResult search(String query) {
		return mvc.get().uri("/api/geo/search").param("query", query).exchange();
	}

	private MvcTestResult reverse(String lat, String lng) {
		return mvc.get().uri("/api/geo/reverse").param("lat", lat).param("lng", lng).exchange();
	}

	private static GeocodeAddress seoul(String road, String jibun, String english, double lat, double lng) {
		return new GeocodeAddress(road, jibun, english, "서울특별시", lat, lng);
	}
}
