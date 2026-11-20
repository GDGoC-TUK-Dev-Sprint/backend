package com.tukorea.gdgoc.koready.infra.koreaexim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 한국수출입은행 현재환율 Open API (data=AP01). 고시일 하나의 매매기준율을 통화별 "1단위당 원화"로 준다.
 * <ul>
 *   <li>JPY는 100엔 기준({@code JPY(100)})으로 오므로 100으로 나눠 1엔 기준으로 바꾼다</li>
 *   <li>금액은 {@code "1,234.5"}처럼 쉼표가 붙은 문자열로 온다</li>
 *   <li>주말·공휴일·고시 전(오전 11시쥔 전)에는 빈 배열이 온다 → 빈 맵</li>
 * </ul>
 * 호출이 실패하거나(연결·시간 초과·HTTP 오류) 오류 결과 코드가 오거나 응답을 읽지 못하면 {@code 502 EXTERNAL_API_FAILED}다.
 */
@Component
public class KoreaEximClient {

	static final String PATH = "/site/program/financial/exchangeJSON";

	/** 결과 코드: 1 성공, 2 DATA 코드 오류, 3 인증키 오류, 4 일일 호출 한도 초과 */
	private static final int SUCCESS = 1;

	private static final DateTimeFormatter SEARCH_DATE = DateTimeFormatter.BASIC_ISO_DATE;
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final RestClient restClient;
	private final String authKey;

	@Autowired
	KoreaEximClient(RestClient.Builder builder, KoreaEximProperties properties) {
		this(builder.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory(properties))
				.build(), properties.authKey());
	}

	KoreaEximClient(RestClient restClient, String authKey) {
		this.restClient = restClient;
		this.authKey = authKey;
	}

	/** 인증키가 있는지. 없으면 환율을 수집하지 않는다 */
	public boolean isConfigured() {
		return authKey != null && !authKey.isBlank();
	}

	/**
	 * 그 고시일의 통화 코드({@code USD}, {@code JPY} …)별 1단위당 원화 (JPY는 1엔 기준). 고시가 없는 날은 빈 맵.
	 * 어떤 통화를 저장할지는 부르는 쪽이 정한다.
	 */
	public Map<String, BigDecimal> fetchRates(LocalDate date) {
		if (!isConfigured()) {
			throw new ApiException(ErrorCode.EXTERNAL_API_FAILED, "한국수출입은행 인증키가 없습니다");
		}
		try {
			String text = restClient.get()
					.uri(PATH + "?authkey={authKey}&searchdate={date}&data=AP01", authKey, date.format(SEARCH_DATE))
					.retrieve()
					.body(String.class);
			RateItem[] items = text == null || text.isBlank() ? new RateItem[0] : JSON.readValue(text, RateItem[].class);
			Map<String, BigDecimal> rates = new LinkedHashMap<>();
			for (RateItem item : items) {
				if (item.result() != null && item.result() != SUCCESS) {
					throw new ApiException(ErrorCode.EXTERNAL_API_FAILED, "한국수출입은행 환율 오류 결과 코드: " + item.result());
				}
				Rate rate = toRate(item);
				if (rate != null) {
					rates.put(rate.currencyCode(), rate.krwPerUnit());
				}
			}
			return rates;
		} catch (ApiException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new ApiException(ErrorCode.EXTERNAL_API_FAILED, "한국수출입은행 환율 호출에 실패했습니다", e);
		}
	}

	/** {@code USD}, {@code JPY(100)}처럼 온 통화 단위와 매매기준율을 1단위당 원화로. 읽지 못한 값은 null */
	private static Rate toRate(RateItem item) {
		if (item.curUnit() == null || item.dealBasR() == null) {
			return null;
		}
		String unit = item.curUnit().trim();
		int divisor = 1;
		int paren = unit.indexOf('(');
		if (paren > 0) {
			try {
				divisor = Integer.parseInt(unit.substring(paren + 1, unit.indexOf(')', paren)).trim());
			} catch (RuntimeException e) {
				return null;
			}
			unit = unit.substring(0, paren).trim();
		}
		if (unit.isEmpty()) {
			return null;
		}
		BigDecimal dealBasR;
		try {
			dealBasR = new BigDecimal(item.dealBasR().replace(",", "").trim());
		} catch (NumberFormatException e) {
			return null;
		}
		if (dealBasR.signum() <= 0 || divisor <= 0) {
			return null;
		}
		return new Rate(unit, dealBasR.divide(BigDecimal.valueOf(divisor), 4, RoundingMode.HALF_UP));
	}

	private static JdkClientHttpRequestFactory requestFactory(KoreaEximProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(properties.readTimeout());
		return factory;
	}

	private record Rate(String currencyCode, BigDecimal krwPerUnit) {
	}

	/** 응답 한 줄 (필요한 필드만). result는 오류일 때만 1이 아니다 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record RateItem(
			Integer result,
			@JsonProperty("cur_unit") String curUnit,
			@JsonProperty("deal_bas_r") String dealBasR) {
	}
}
