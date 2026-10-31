package com.tukorea.gdgoc.koready.trip;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 여행 조건 설정값 ({@code koready.trip.*}).
 *
 * @param maxDays 여행 최대 일수
 * @param maxPartySize 최대 인원
 */
@ConfigurationProperties("koready.trip")
record TripProperties(int maxDays, int maxPartySize) {
}
