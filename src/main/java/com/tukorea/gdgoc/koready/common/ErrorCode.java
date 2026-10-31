package com.tukorea.gdgoc.koready.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 오류 응답 코드. 응답 {@code code}는 이 이름이고, {@code message}는 개발 확인용이다 (화면 문구는 앱이 code로 정한다).
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),
	OUT_OF_SERVICE_AREA(HttpStatus.BAD_REQUEST, "숙소 위치가 서울 밖입니다"),
	TOO_MANY_PLACES(HttpStatus.BAD_REQUEST, "담은 장소가 일수 × 하루 최대 수를 넘습니다"),
	PLACE_NOT_SELECTABLE(HttpStatus.BAD_REQUEST, "후보가 아닌 장소입니다"),
	ORDER_MISMATCH(HttpStatus.BAD_REQUEST, "순서 변경 목록이 그 일자의 항목과 다릅니다"),

	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다"),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료됐습니다"),
	LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디·비밀번호가 맞지 않습니다"),
	INVALID_ID_TOKEN(HttpStatus.UNAUTHORIZED, "소셜 로그인 ID 토큰을 확인하지 못했습니다"),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "리프레시 토큰이 없거나 만료됐거나 이미 썼습니다"),

	TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 여행입니다"),
	DAY_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 일자입니다"),
	ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 일정 항목입니다"),
	PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "없는 장소입니다"),

	LOGIN_ID_TAKEN(HttpStatus.CONFLICT, "이미 쓰는 아이디입니다"),
	ACTIVE_TRIP_EXISTS(HttpStatus.CONFLICT, "끝나지 않은 확정 여행이 있습니다"),
	TRIP_CONFIRMED(HttpStatus.CONFLICT, "확정한 여행은 편집할 수 없습니다"),
	ROUTE_NOT_CREATED(HttpStatus.CONFLICT, "동선을 만들기 전입니다"),
	TRIP_NOT_COMPLETED(HttpStatus.CONFLICT, "끝난 확정 여행이 아닙니다"),
	EXPENSES_NOT_SAVED(HttpStatus.CONFLICT, "실제 지출을 저장하기 전입니다"),

	TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "호출 한도를 넘었습니다"),

	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류입니다"),
	EXTERNAL_API_FAILED(HttpStatus.BAD_GATEWAY, "외부 API 호출에 실패했습니다"),
	LLM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "LLM 호출에 실패했습니다");

	private final HttpStatus status;
	private final String message;
}
