package com.tukorea.gdgoc.koready.infra.naver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

/**
 * 네이버 지역 검색(NAVER API HUB, 장소명 → 업체·기관). 키 헤더와 시간 제한을 여기서 붙이고,
 * 호출이 실패하거나(연결·시간 초과·HTTP 오류) 응답을 읽지 못하면 {@code 502 EXTERNAL_API_FAILED}로 바꾼다.
 * <p>
 * 네이버 이용 조건: 결과는 순서·내용을 바꾸지 않고 다른 결과와 섞지 않으며 출처(링크)와 함께 보인다.
 * 저장·캐시하지 않고, AI(LLM) 입력에 쓰지 않는다.
 */
@Component
public class NaverSearchClient {

	static final String LOCAL_PATH = "/search/v1/local";
	/** 한 번에 받을 수 있는 최대 결과 수. 넘기면 네이버가 오류 없이 빈 결과를 준다 */
	public static final int MAX_DISPLAY = 5;

	/** mapx·mapy는 경도·위도(WGS84)에 10^7을 곱한 정수 문자열이다 */
	private static final int COORDINATE_SCALE = 7;
	/** title에서 검색어와 맞은 부분을 감싸는 강조 태그 */
	private static final Pattern BOLD_TAG = Pattern.compile("</?b>");

	private final RestClient restClient;

	@Autowired
	NaverSearchClient(RestClient.Builder builder, NaverSearchProperties properties) {
		this(configure(builder, properties)
				.requestFactory(NaverHttp.requestFactory(properties.connectTimeout(), properties.readTimeout()))
				.build());
	}

	NaverSearchClient(RestClient restClient) {
		this.restClient = restClient;
	}

	/**
	 * 주소와 키 헤더를 붙인다. Accept를 JSON으로 두지 않으면 네이버가 text/plain으로 줘 읽지 못한다.
	 * 시간 제한은 생성자에서 붙인다 (테스트는 가짜 서버를 이 빌더에 묶는다)
	 */
	static RestClient.Builder configure(RestClient.Builder builder, NaverSearchProperties properties) {
		return builder.baseUrl(properties.baseUrl())
				.defaultHeader("X-NCP-APIGW-API-KEY-ID", Objects.requireNonNullElse(properties.clientId(), ""))
				.defaultHeader("X-NCP-APIGW-API-KEY", Objects.requireNonNullElse(properties.clientSecret(), ""))
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
	}

	/**
	 * 장소명 검색 결과를 네이버 순서(정확도순)대로 최대 count개(1~{@value #MAX_DISPLAY}) 준다.
	 * 이름은 강조 태그({@code <b>})를 빼고 HTML 문자 표기({@code &amp;} 등)를 글자로 바꾼다. 좌표를 읽지 못한 곳은 뺀다
	 */
	public List<LocalSearchPlace> searchLocal(String query, int count) {
		Assert.isTrue(count >= 1 && count <= MAX_DISPLAY, "count는 1~" + MAX_DISPLAY + "입니다");
		return NaverHttp.call("지역 검색", () -> {
			LocalBody body = restClient.get()
					.uri(LOCAL_PATH + "?query={query}&display={display}", query, count)
					.retrieve()
					.body(LocalBody.class);
			if (body == null || body.items() == null) {
				throw new ApiException(ErrorCode.EXTERNAL_API_FAILED, "네이버 지역 검색 응답에 items가 없습니다");
			}
			return body.items().stream()
					.map(NaverSearchClient::place)
					.filter(Objects::nonNull)
					.toList();
		});
	}

	/** 좌표를 읽지 못하면 null */
	private static LocalSearchPlace place(LocalItem item) {
		Double lng = coordinate(item.mapx());
		Double lat = coordinate(item.mapy());
		if (lat == null || lng == null) {
			return null;
		}
		String title = item.title() == null ? null : BOLD_TAG.matcher(item.title()).replaceAll("");
		return new LocalSearchPlace(text(title), blankToNull(item.link()), text(item.roadAddress()),
				text(item.address()), lat, lng);
	}

	private static Double coordinate(String value) {
		if (blankToNull(value) == null) {
			return null;
		}
		try {
			return new BigDecimal(value.strip()).movePointLeft(COORDINATE_SCALE).doubleValue();
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** HTML 문자 표기를 글자로 바꾼다. 비어 있으면 null */
	private static String text(String value) {
		return blankToNull(value) == null ? null : HtmlUtils.htmlUnescape(value);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	/** 지역 검색 응답 (필요한 필드만) */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record LocalBody(List<LocalItem> items) {
	}

	/** address는 지번 주소다. mapx(경도)·mapy(위도)는 정수 문자열로 온다 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record LocalItem(String title, String link, String address, String roadAddress, String mapx, String mapy) {
	}
}
