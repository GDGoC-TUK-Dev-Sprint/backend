package com.tukorea.gdgoc.koready.infra.odsay;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * ODsay 설정값 ({@code koready.odsay.*}). 키는 환경변수로만 받고, 없으면 ODsay를 부르지 않는다 (이동 구간은 좌표 추정값).
 *
 * @param baseUrl API 주소
 * @param apiKey 서버 플랫폼 키 (쿼리 파라미터 {@code apiKey}). 부르는 PC·서버의 공인 IP를 ODsay LAB 애플리케이션에 등록한다
 * @param connectTimeout 연결 시간 제한
 * @param readTimeout 응답 시간 제한. 한 구간만 부를 때는 호출 차례를 기다리는 시간도 이 값을 넘기지 않는다
 * @param batchTimeout 여러 구간을 한 번에 받을 때(동선 만들기 등) 전체 시간 제한. 이 안에 받지 못한 구간은 좌표 추정값이다
 * @param minInterval 호출을 시작하는 최소 간격 (서버 전체). 초당 호출 수를 넘기면 ODsay가 429로 거절한다
 * @param rateLimitBackoff 429를 받은 뒤 다음 호출을 시작하기까지 더 기다리는 시간 (초당 호출 수 초과가 풀리기를 기다린다)
 * @param quotaDetectWindow 429가 이 시간 넘게 이어지면(그사이 성공 없음) 일일 호출 한도를 넘긴 것으로 본다
 * @param quotaRetryInterval 일일 호출 한도를 넘긴 뒤 다시 부르기까지 쉬는 시간. 그동안은 좌표 추정값을 쓴다
 * @param failureRetryInterval 연결 실패·시간 초과·HTTP 5xx 뒤 다시 부르기까지 쉬는 시간 (ODsay가 멈춘 동안 요청마다 시간 제한만큼 기다리지 않게)
 */
@Validated
@ConfigurationProperties("koready.odsay")
record OdsayProperties(@NotBlank String baseUrl, String apiKey, @NotNull Duration connectTimeout,
		@NotNull Duration readTimeout, @NotNull Duration batchTimeout, @NotNull Duration minInterval,
		@NotNull Duration rateLimitBackoff, @NotNull Duration quotaDetectWindow, @NotNull Duration quotaRetryInterval,
		@NotNull Duration failureRetryInterval) {
}
