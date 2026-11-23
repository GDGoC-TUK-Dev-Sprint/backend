package com.tukorea.gdgoc.koready.domain.trip.dto;

/**
 * 동선에 넣지 못한 이유.
 */
public enum UnscheduledReason {
	/** 여행 기간 내내 휴무 */
	CLOSED_DURING_TRIP,
	/** 하루 시간이 모자람 (영업시간 안에 머물고 하루 끝 시각 전에 숙소로 돌아올 날이 없음) */
	NOT_ENOUGH_TIME
}
