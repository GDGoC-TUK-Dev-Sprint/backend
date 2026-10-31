package com.tukorea.gdgoc.koready.meta;

/**
 * 카테고리: 조건 입력에서 고르는 여행 테마. TourAPI 신분류 대분류 중 숙박(AC)·추천코스(C01)를 뺀 8개이고,
 * 상수 이름이 대분류 코드다. 표시 이름은 {@code place_classes}의 분류명 그대로 쓴다. 선언 순서가 화면에 보일 순서다.
 */
public enum Category {

	/** 쇼핑 */
	SH,
	/** 음식 */
	FD,
	/** 문화관광 */
	VE,
	/** 역사관광 */
	HS,
	/** 축제/공연/행사 */
	EV,
	/** 체험관광 */
	EX,
	/** 자연관광 */
	NA,
	/** 레저스포츠 */
	LS
}
