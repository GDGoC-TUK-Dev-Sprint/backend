package com.tukorea.gdgoc.koready.domain.trip.entity;

/**
 * 일정 항목이 들어온 경로. 값은 {@code origin varchar(10)}에 들어가고, DB CHECK 제약은 {@code schema.sql}이 이 값들로 다시 만든다
 * (값을 더하면 거기도 더한다).
 */
public enum TripItemOrigin {
	/** 장소 선택에서 담음 */
	SELECTED,
	/** 담은 장소가 권장 최소보다 적어 동선 만들기가 추천순으로 채움 */
	AUTO_FILL,
	/** 식사 자리 자동 */
	MEAL
}
