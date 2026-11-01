package com.tukorea.gdgoc.koready.candidate;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 장소 후보 설정값 ({@code koready.candidate.*}). 값이 빠지면 서버가 뜨지 않는다.
 *
 * @param signalWeightM 순위 점수에서 인기도 신호 1개가 맞먹는 숙소 거리(m). 점수 = 신호 개수 × 이 값 − 숙소 직선거리
 * @param nearStayM 근거 태그 {@code NEAR_STAY}("숙소 가까움")를 붙이는 숙소 직선거리(m)
 * @param recommendedMinPerDay 권장 장소 수 최소 = 일수 × 이 값
 * @param recommendedMaxPerDay 권장 장소 수 최대 = 일수 × 이 값
 * @param maxPerDay 하루에 넣을 수 있는 최대 장소 수
 */
@Validated
@ConfigurationProperties("koready.candidate")
public record CandidateProperties(
		@Min(1) int signalWeightM,
		@Min(0) int nearStayM,
		@Min(1) int recommendedMinPerDay,
		@Min(1) int recommendedMaxPerDay,
		@Min(1) int maxPerDay) {
}
