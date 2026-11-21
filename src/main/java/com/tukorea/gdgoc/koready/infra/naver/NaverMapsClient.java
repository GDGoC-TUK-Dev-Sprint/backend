package com.tukorea.gdgoc.koready.infra.naver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 네이버 클라우드 Maps Geocoding(주소 → 좌표)·Reverse Geocoding(좌표 → 주소). 키 헤더와 시간 제한을 여기서 붙이고,
 * 호출이 실패하거나(연결·시간 초과·HTTP 오류) 네이버가 오류 상태를 주거나 응답을 읽지 못하면 {@code 502 EXTERNAL_API_FAILED}로 바꾼다.
 */
@Component
public class NaverMapsClient {

	static final String GEOCODE_PATH = "/map-geocode/v2/geocode";
	static final String REVERSE_GEOCODE_PATH = "/map-reversegeocode/v2/gc";

	/** Reverse Geocoding 상태 코드: 0 정상, 3 결과 없음 (바다·국외 등) */
	private static final int OK = 0;
	private static final int NO_RESULTS = 3;

	private final RestClient restClient;

	@Autowired
	NaverMapsClient(RestClient.Builder builder, NaverMapsProperties properties) {
		this(configure(builder, properties)
				.requestFactory(NaverHttp.requestFactory(properties.connectTimeout(), properties.readTimeout()))
				.build());
	}

	NaverMapsClient(RestClient restClient) {
		this.restClient = restClient;
	}

	/** 주소와 키 헤더를 붙인다. 시간 제한은 생성자에서 붙인다 (테스트는 가짜 서버를 이 빌더에 묶는다) */
	static RestClient.Builder configure(RestClient.Builder builder, NaverMapsProperties properties) {
		return builder.baseUrl(properties.baseUrl())
				.defaultHeader("x-ncp-apigw-api-key-id", Objects.requireNonNullElse(properties.clientId(), ""))
				.defaultHeader("x-ncp-apigw-api-key", Objects.requireNonNullElse(properties.clientSecret(), ""))
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
	}

	/** 주소 검색 결과를 네이버 순서대로 최대 count개 준다. 네이버는 주소만 찾는다 (장소명은 결과가 없다). 좌표를 읽지 못한 곳은 뺀다 */
	public List<GeocodeAddress> geocode(String query, int count) {
		return NaverHttp.call("Geocoding", () -> {
			GeocodeBody body = restClient.get()
					.uri(GEOCODE_PATH + "?query={query}&count={count}", query, count)
					.retrieve()
					.body(GeocodeBody.class);
			if (body == null || !"OK".equals(body.status())) {
				throw new ApiException(ErrorCode.EXTERNAL_API_FAILED,
						"네이버 Geocoding 오류 상태: " + (body == null ? null : body.status()));
			}
			return body.addresses() == null ? List.of() : body.addresses().stream()
					.map(NaverMapsClient::address)
					.filter(Objects::nonNull)
					.toList();
		});
	}

	/** 좌표의 주소와 법정동 코드. 결과가 없으면(바다·국외) 모두 null이다 */
	public ReverseGeocodeResult reverseGeocode(double lat, double lng) {
		return NaverHttp.call("Reverse Geocoding", () -> {
			ReverseBody body = restClient.get()
					.uri(REVERSE_GEOCODE_PATH + "?coords={lng},{lat}&orders=legalcode,roadaddr,addr&output=json",
							BigDecimal.valueOf(lng).toPlainString(), BigDecimal.valueOf(lat).toPlainString())
					.retrieve()
					.body(ReverseBody.class);
			Integer code = body == null || body.status() == null ? null : body.status().code();
			if (Objects.equals(code, NO_RESULTS)) {
				return new ReverseGeocodeResult(null, null, null);
			}
			if (!Objects.equals(code, OK)) {
				throw new ApiException(ErrorCode.EXTERNAL_API_FAILED, "네이버 Reverse Geocoding 오류 상태: " + code);
			}
			String legalCode = null;
			String roadAddress = null;
			String jibunAddress = null;
			for (ReverseResult result : body.results() == null ? List.<ReverseResult>of() : body.results()) {
				switch (Objects.requireNonNullElse(result.name(), "")) {
					case "legalcode" -> legalCode = result.code() == null ? null : blankToNull(result.code().id());
					case "roadaddr" -> roadAddress = roadAddress(result);
					case "addr" -> jibunAddress = jibunAddress(result);
					default -> {
					}
				}
			}
			return new ReverseGeocodeResult(legalCode, roadAddress, jibunAddress);
		});
	}

	/** 좌표(x 경도·y 위도 문자열)를 읽지 못하면 null */
	private static GeocodeAddress address(GeocodeItem item) {
		if (item.x() == null || item.y() == null) {
			return null;
		}
		double lat;
		double lng;
		try {
			lat = Double.parseDouble(item.y());
			lng = Double.parseDouble(item.x());
		} catch (NumberFormatException e) {
			return null;
		}
		String sido = item.addressElements() == null ? null : item.addressElements().stream()
				.filter(element -> element.types() != null && element.types().contains("SIDO"))
				.map(element -> blankToNull(element.longName()))
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
		return new GeocodeAddress(blankToNull(item.roadAddress()), blankToNull(item.jibunAddress()),
				blankToNull(item.englishAddress()), sido, lat, lng);
	}

	/** 도로명 주소: 시도 시군구 (읍·면) 도로명 건물번호 건물 이름. 동은 도로명 주소에 들어가지 않는다 */
	private static String roadAddress(ReverseResult result) {
		Land land = result.land();
		if (land == null || blankToNull(land.name()) == null || blankToNull(land.number1()) == null) {
			return null;
		}
		String town = name(result.region(), 3);
		boolean eupMyeon = town != null && (town.endsWith("읍") || town.endsWith("면"));
		String building = land.addition0() != null && "building".equals(land.addition0().type())
				? land.addition0().value()
				: null;
		return join(name(result.region(), 1), name(result.region(), 2), eupMyeon ? town : null, land.name(),
				number(land), building);
	}

	/** 지번 주소: 시도 시군구 읍면동 리 (산)본번-부번 */
	private static String jibunAddress(ReverseResult result) {
		Land land = result.land();
		if (land == null || blankToNull(land.number1()) == null) {
			return null;
		}
		String mountain = "2".equals(land.type()) ? "산" : "";
		return join(name(result.region(), 1), name(result.region(), 2), name(result.region(), 3),
				name(result.region(), 4), mountain + number(land));
	}

	private static String number(Land land) {
		return blankToNull(land.number2()) == null ? land.number1() : land.number1() + "-" + land.number2();
	}

	private static String name(Region region, int level) {
		if (region == null) {
			return null;
		}
		Area area = switch (level) {
			case 1 -> region.area1();
			case 2 -> region.area2();
			case 3 -> region.area3();
			default -> region.area4();
		};
		return area == null ? null : blankToNull(area.name());
	}

	private static String join(String... parts) {
		return Arrays.stream(parts).filter(part -> blankToNull(part) != null).collect(Collectors.joining(" "));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	/** Geocoding 응답 (필요한 필드만) */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record GeocodeBody(String status, List<GeocodeItem> addresses) {
	}

	/** 좌표 x(경도)·y(위도)는 문자열로 온다 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record GeocodeItem(String roadAddress, String jibunAddress, String englishAddress,
			List<AddressElement> addressElements, String x, String y) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AddressElement(List<String> types, String longName) {
	}

	/** Reverse Geocoding 응답 (필요한 필드만) */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record ReverseBody(ReverseStatus status, List<ReverseResult> results) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ReverseStatus(Integer code, String name) {
	}

	/** name: legalcode(법정동)·roadaddr(도로명)·addr(지번) */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record ReverseResult(String name, ResultCode code, Region region, Land land) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ResultCode(String id) {
	}

	/** area1 시도, area2 시군구, area3 읍면동, area4 리 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Region(Area area1, Area area2, Area area3, Area area4) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Area(String name) {
	}

	/** type: 지번 1 일반·2 산. 도로명은 name(도로명)·number1(건물번호)·addition0(건물 이름) */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Land(String type, String name, String number1, String number2, Addition addition0) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Addition(String type, String value) {
	}
}
