package com.tukorea.gdgoc.koready.infra.koreaexim;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 한국수출입은행 현재환율 Open API 설정값 ({@code koready.koreaexim.*}). 인증키는 환경변수로만 받고,
 * 없어도 서버는 뜨지만 환율을 수집하지 않는다 (원화가 아닌 통화로 환산하면 503 EXCHANGE_RATE_UNAVAILABLE).
 *
 * @param baseUrl API 주소
 * @param authKey 인증키 (쿼리 {@code authkey})
 * @param connectTimeout 연결 시간 제한
 * @param readTimeout 응답 시간 제한
 */
@Validated
@ConfigurationProperties("koready.koreaexim")
record KoreaEximProperties(@NotBlank String baseUrl, String authKey,
		@NotNull Duration connectTimeout, @NotNull Duration readTimeout) {
}
