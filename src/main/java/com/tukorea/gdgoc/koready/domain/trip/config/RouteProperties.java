package com.tukorea.gdgoc.koready.domain.trip.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 동선 계산 설정값 ({@code koready.route.*}). 이동 구간을 좌표로 추정할 때와 시각을 이어 붙일 때 쓴다.
 *
 * @param dayStart 숙소 출발 시각이 없을 때 쓰는 하루 시작 시각
 * @param walkMaxDistanceM 직선거리가 이 값(m) 이하면 도보, 넘으면 대중교통으로 추정한다
 * @param detourFactor 직선거리를 실제 이동 거리로 바꾸는 보정 배수
 * @param walkMetersPerMinute 도보 속도
 * @param transitMetersPerMinute 대중교통 속도 (타고 있는 동안)
 * @param transitWaitMinutes 대중교통 대기·환승·역까지 걷는 시간
 */
@Validated
@ConfigurationProperties("koready.route")
public record RouteProperties(
		@NotNull LocalTime dayStart,
		@Min(1) int walkMaxDistanceM,
		@DecimalMin("1.0") double detourFactor,
		@DecimalMin("1.0") double walkMetersPerMinute,
		@DecimalMin("1.0") double transitMetersPerMinute,
		@Min(0) int transitWaitMinutes) {
}
