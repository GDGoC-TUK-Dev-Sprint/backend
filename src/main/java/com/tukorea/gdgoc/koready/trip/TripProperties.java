package com.tukorea.gdgoc.koready.trip;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 여행 조건 설정값 ({@code koready.trip.*}). 값이 빠지면 0이 되어 모든 요청을 거절하므로, 그때는 서버가 뜨지 않는다.
 *
 * @param maxDays 여행 최대 일수
 * @param maxPartySize 최대 인원
 */
@Validated
@ConfigurationProperties("koready.trip")
record TripProperties(@Min(1) int maxDays, @Min(1) int maxPartySize) {
}
