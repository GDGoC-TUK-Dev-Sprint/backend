package com.tukorea.gdgoc.koready.trip;

/**
 * 여행 진행 상태. 저장하지 않고 서울 날짜로 계산한다 ({@link Trip#progressOn}).
 */
public enum TripProgress {
	/** 진행 예정 */
	UPCOMING,
	/** 진행 중 */
	ONGOING,
	/** 완료 */
	COMPLETED
}
