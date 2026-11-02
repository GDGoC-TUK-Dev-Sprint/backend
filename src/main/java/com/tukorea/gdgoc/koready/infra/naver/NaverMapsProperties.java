package com.tukorea.gdgoc.koready.infra.naver;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 네이버 클라우드 Maps 설정값 ({@code koready.naver-maps.*}). 키는 환경변수로만 받고, 없어도 서버는 뜨지만 호출이 실패한다 (502).
 *
 * @param baseUrl API 게이트웨이 주소
 * @param clientId Client ID (헤더 {@code x-ncp-apigw-api-key-id})
 * @param clientSecret Client Secret (헤더 {@code x-ncp-apigw-api-key}). 앱에 넣지 않는다
 * @param connectTimeout 연결 시간 제한
 * @param readTimeout 응답 시간 제한
 */
@Validated
@ConfigurationProperties("koready.naver-maps")
record NaverMapsProperties(@NotBlank String baseUrl, String clientId, String clientSecret,
		@NotNull Duration connectTimeout, @NotNull Duration readTimeout) {
}
