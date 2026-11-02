package com.tukorea.gdgoc.koready.domain.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import com.tukorea.gdgoc.koready.infra.naver.GeocodeAddress;
import com.tukorea.gdgoc.koready.infra.naver.LocalSearchPlace;
import com.tukorea.gdgoc.koready.infra.naver.NaverMapsClient;
import com.tukorea.gdgoc.koready.infra.naver.NaverSearchClient;
import com.tukorea.gdgoc.koready.infra.naver.ReverseGeocodeResult;
import com.tukorea.gdgoc.koready.support.IntegrationTest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * 지도에서 숙소 위치 선택의 주소·장소명 검색과 핀 위치 주소. 로그인 없이 부른다.
 * 네이버 클라이언트는 가짜다 (호출·응답 읽기는 NaverMapsClientTest·NaverSearchClientTest).
 */
@IntegrationTest
class GeoTest {

	private static final GeocodeAddress MYEONGDONG = seoul("서울특별시 중구 명동", "서울특별시 중구 명동",
			"Myeong-dong, Jung-gu, Seoul, Republic of Korea", 37.5609, 126.9863);
	private static final GeocodeAddress CHUNCHEON = new GeocodeAddress("강원특별자치도 춘천시 명동길 14",
			"강원특별자치도 춘천시 중앙로2가 23-1", "14, Myeongdong-gil, Chuncheon-si, Gangwon-do, Republic of Korea",
			"강원특별자치도", 37.8793856, 127.7270855);
	private static final LocalSearchPlace MYEONGDONG_STATION = new LocalSearchPlace("명동역 4호선", null,
			"서울특별시 중구 퇴계로 126", "서울특별시 중구 충무로2가 109-2", 37.560972, 126.9864649);
	private static final LocalSearchPlace MYEONGDONG_KYOJA = new LocalSearchPlace("명동교자 1호점",
			"http://www.mdkj.co.kr/", "서울특별시 중구 명동10길 10 명동교자", "서울특별시 중구 명동2가 33-4 명동교자",
			37.5634828, 126.9851666);

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	NaverMapsClient naverMaps;

	@MockitoBean
	NaverSearchClient naverSearch;

	/** 주소 결과(서울 먼저, 최대 5개) 다음에 장소 결과(네이버 지역 검색, 네이버 순서 그대로 최대 5개)다 */
	@Test
	void addressesThenPlaces() {
		given(naverMaps.geocode("명동", 20)).willReturn(List.of(
				CHUNCHEON,
				MYEONGDONG,
				seoul("서울특별시 중구 명동길 14 Noon Square", null, null, 37.5634327, 126.9828804),
				seoul(null, "서울특별시 중구 명동2가 83-5", null, 37.5634, 126.9828),
				seoul("서울특별시 중구 명동8길 10", null, null, 37.5612, 126.9851),
				seoul("서울특별시 중구 명동10길 19", null, null, 37.5620, 126.9858)));
		given(naverSearch.searchLocal("명동", 5)).willReturn(List.of(MYEONGDONG_STATION, MYEONGDONG_KYOJA));

		MvcTestResult result = search("명동");

		assertThat(result).hasStatusOk().bodyJson().extractingPath("$.items[*].type").isEqualTo(List.of(
				"ADDRESS", "ADDRESS", "ADDRESS", "ADDRESS", "ADDRESS", "PLACE", "PLACE"));
		assertThat(result).bodyJson().extractingPath("$.items[*].label").isEqualTo(List.of(
				// 서울 주소가 네이버 순서대로 먼저 오고, 서울 밖(춘천)은 6번째로 밀려 빠진다. 도로명이 없으면 지번
				"서울특별시 중구 명동", "서울특별시 중구 명동길 14 Noon Square", "서울특별시 중구 명동2가 83-5",
				"서울특별시 중구 명동8길 10", "서울특별시 중구 명동10길 19",
				"명동역 4호선", "명동교자 1호점"));
		assertThat(result).bodyJson().extractingPath("$.items[*].inSeoul").asArray().hasSize(7).containsOnly(true);
		assertThat(result).bodyJson().extractingPath("$.items[0].location")
				.isEqualTo(Map.of("lat", 37.5609, "lng", 126.9863));
		assertThat(result).bodyJson().extractingPath("$.items[0].link").isNull();
		assertThat(result).bodyJson().extractingPath("$.items[5].address").isEqualTo("서울특별시 중구 퇴계로 126");
		assertThat(result).bodyJson().extractingPath("$.items[5].location")
				.isEqualTo(Map.of("lat", 37.560972, "lng", 126.9864649));
		assertThat(result).bodyJson().extractingPath("$.items[5].link").isNull();
		assertThat(result).bodyJson().extractingPath("$.items[6].link").isEqualTo("http://www.mdkj.co.kr/");
	}

	/** 서울 밖 주소는 서울 주소 뒤에 inSeoul = false로 온다. 주소가 없는 결과는 뺀다 */
	@Test
	void addressOutsideSeoul() {
		given(naverMaps.geocode("명동길 14", 20)).willReturn(List.of(
				CHUNCHEON,
				new GeocodeAddress(null, null, null, "서울특별시", 37.56, 126.98),
				seoul("서울특별시 중구 명동길 14 Noon Square", null, null, 37.5634327, 126.9828804)));
		given(naverSearch.searchLocal("명동길 14", 5)).willReturn(List.of());

		assertThat(search("명동길 14")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"items": [
				  {"type": "ADDRESS", "label": "서울특별시 중구 명동길 14 Noon Square",
				   "address": "서울특별시 중구 명동길 14 Noon Square",
				   "location": {"lat": 37.5634327, "lng": 126.9828804}, "inSeoul": true, "link": null},
				  {"type": "ADDRESS", "label": "강원특별자치도 춘천시 명동길 14", "address": "강원특별자치도 춘천시 명동길 14",
				   "location": {"lat": 37.8793856, "lng": 127.7270855}, "inSeoul": false, "link": null}]}
				""");
	}

	/** 영어로 요청하면 주소 결과는 영문 주소(없으면 국문)다. 장소 결과는 네이버가 준 국문 그대로다 */
	@Test
	void englishRequest() {
		given(naverMaps.geocode("명동", 20)).willReturn(List.of(
				MYEONGDONG, seoul("서울특별시 중구 명동길 14 Noon Square", null, null, 37.5634327, 126.9828804)));
		given(naverSearch.searchLocal("명동", 5)).willReturn(List.of(MYEONGDONG_KYOJA));

		MvcTestResult result = mvc.get().uri("/api/geo/search").param("query", "명동").header("Accept-Language", "en")
				.exchange();

		assertThat(result).hasStatusOk().bodyJson().extractingPath("$.items[*].label").isEqualTo(List.of(
				"Myeong-dong, Jung-gu, Seoul, Republic of Korea", "서울특별시 중구 명동길 14 Noon Square", "명동교자 1호점"));
		assertThat(result).bodyJson().extractingPath("$.items[0].address")
				.isEqualTo("Myeong-dong, Jung-gu, Seoul, Republic of Korea");
		assertThat(result).bodyJson().extractingPath("$.items[2].address").isEqualTo("서울특별시 중구 명동10길 10 명동교자");
	}

	/**
	 * 장소 결과는 서울 밖이 섞여 있어도 네이버 순서 그대로다 (주소 결과처럼 서울을 앞으로 올리지 않는다).
	 * 주소는 도로명, 없으면 지번이고, 서울인지는 그 주소의 시도 이름으로 본다
	 */
	@Test
	void placeResultsKeepNaverOrder() {
		given(naverMaps.geocode("경복궁", 20)).willReturn(List.of());
		given(naverSearch.searchLocal("경복궁", 5)).willReturn(List.of(
				new LocalSearchPlace("경복궁 한옥마을점", "http://www.entas.co.kr/", "인천광역시 연수구 테크노파크로 180",
						"인천광역시 연수구 송도동 24-17", 37.3907929, 126.6388695),
				new LocalSearchPlace("경복궁", "https://royal.khs.go.kr/gbg", null, "서울특별시 종로구 세종로 1-1 경복궁",
						37.5788408, 126.9770162),
				new LocalSearchPlace("주소 없음", null, null, null, 37.5, 127.0)));

		MvcTestResult result = search("경복궁");

		assertThat(result).hasStatusOk().bodyJson().extractingPath("$.items[*].label")
				.isEqualTo(List.of("경복궁 한옥마을점", "경복궁", "주소 없음"));
		assertThat(result).bodyJson().extractingPath("$.items[*].address").isEqualTo(Arrays.asList(
				"인천광역시 연수구 테크노파크로 180", "서울특별시 종로구 세종로 1-1 경복궁", null));
		assertThat(result).bodyJson().extractingPath("$.items[*].inSeoul").isEqualTo(List.of(false, true, false));
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
		verifyNoInteractions(naverMaps, naverSearch);
	}

	/** Geocoding·지역 검색 중 하나라도 실패하면 성공한 쪽 결과도 주지 않고 502다 */
	@Test
	void searchNaverFailure() {
		given(naverMaps.geocode("명동", 20)).willThrow(new ApiException(ErrorCode.EXTERNAL_API_FAILED));
		assertThat(search("명동")).hasStatus(HttpStatus.BAD_GATEWAY).bodyJson().isLenientlyEqualTo("""
				{"code": "EXTERNAL_API_FAILED"}
				""");

		given(naverMaps.geocode("명동역", 20)).willReturn(List.of(MYEONGDONG));
		given(naverSearch.searchLocal("명동역", 5)).willThrow(new ApiException(ErrorCode.EXTERNAL_API_FAILED));
		assertThat(search("명동역")).hasStatus(HttpStatus.BAD_GATEWAY).bodyJson().isLenientlyEqualTo("""
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
