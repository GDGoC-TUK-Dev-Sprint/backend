package com.tukorea.gdgoc.koready.candidate;

/**
 * 장소 카드의 근거 태그.
 */
public enum CandidateReason {

	/** 이 여행에서 고른 카테고리의 장소 ("쇼핑 테마"처럼 카테고리 이름 + "테마") */
	CATEGORY_MATCH,
	/** 숙소에서 가깝다 ("숙소 가까움"). 기준 거리는 설정값 */
	NEAR_STAY
}
