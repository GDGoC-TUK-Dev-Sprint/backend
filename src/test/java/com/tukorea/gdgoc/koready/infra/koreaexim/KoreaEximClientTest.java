package com.tukorea.gdgoc.koready.infra.koreaexim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KoreaEximClientTest {

	private static final String BASE = "https://koreaexim.test";
	private static final LocalDate DATE = LocalDate.of(2026, 11, 17);

	private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
	private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
	private final KoreaEximClient client = new KoreaEximClient(builder.build(), "test-key");

	@Test
	void parsesRatesAndConvertsJpyTo1Yen() {
		expectRequest().andRespond(withSuccess("""
				[
				  {"result":1,"cur_unit":"USD","deal_bas_r":"1,398.5","cur_nm":"미국 달러"},
				  {"result":1,"cur_unit":"JPY(100)","deal_bas_r":"905.12","cur_nm":"일본 옐"},
				  {"result":1,"cur_unit":"EUR","deal_bas_r":"1,512.34","cur_nm":"유로"},
				  {"result":1,"cur_unit":"KRW","deal_bas_r":"1","cur_nm":"한국 원"}
				]
				""", MediaType.APPLICATION_JSON));

		var rates = client.fetchRates(DATE);

		assertThat(rates.get("USD")).isEqualByComparingTo(new BigDecimal("1398.5"));
		assertThat(rates.get("JPY")).isEqualByComparingTo(new BigDecimal("9.0512"));
		assertThat(rates.get("EUR")).isEqualByComparingTo(new BigDecimal("1512.34"));
		server.verify();
	}

	@Test
	void emptyArrayOnNonBusinessDay() {
		expectRequest().andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		assertThat(client.fetchRates(DATE)).isEmpty();
	}

	@Test
	void errorResultCodeIs502() {
		expectRequest().andRespond(withSuccess("""
				[{"result":3,"cur_unit":null,"deal_bas_r":null}]
				""", MediaType.APPLICATION_JSON));

		assertExternalApiFailed();
	}

	@Test
	void serverErrorIs502() {
		expectRequest().andRespond(withServerError());

		assertExternalApiFailed();
	}

	@Test
	void notConfiguredWithoutKey() {
		KoreaEximClient noKey = new KoreaEximClient(RestClient.builder().baseUrl(BASE).build(), " ");

		assertThat(noKey.isConfigured()).isFalse();
		assertThatThrownBy(() -> noKey.fetchRates(DATE)).isInstanceOf(ApiException.class);
	}

	private org.springframework.test.web.client.ResponseActions expectRequest() {
		return server.expect(requestTo(startsWith(BASE + KoreaEximClient.PATH)))
				.andExpect(queryParam("authkey", "test-key"))
				.andExpect(queryParam("searchdate", "20261117"))
				.andExpect(queryParam("data", "AP01"));
	}

	private void assertExternalApiFailed() {
		assertThatThrownBy(() -> client.fetchRates(DATE))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_FAILED));
	}
}
