package com.tukorea.gdgoc.koready.common;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 서울 시각. "오늘"·"지금"은 {@link java.time.Clock} 빈(서울)으로 정하고, 기록 시각은 응답할 때 {@code +09:00}으로 준다.
 */
public final class SeoulTime {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private SeoulTime() {
	}

	/** 기록 시각 응답용 ({@code 2026-10-31T14:00:00+09:00}). null이면 null */
	public static OffsetDateTime of(Instant instant) {
		return instant == null ? null : instant.atZone(ZONE).toOffsetDateTime();
	}
}
