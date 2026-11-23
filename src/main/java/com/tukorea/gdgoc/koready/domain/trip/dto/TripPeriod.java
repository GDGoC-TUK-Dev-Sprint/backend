package com.tukorea.gdgoc.koready.domain.trip.dto;

/**
 * 여행 목록의 {@code period}. 둘 다 확정한 여행만 본다.
 */
public enum TripPeriod {
	/** 끝나지 않은(종료일 ≥ 오늘) 여행. 사용자마다 0~1개 */
	CURRENT,
	/** 끝난(종료일이 오늘보다 앞인) 여행 */
	PAST
}
