package com.tukorea.gdgoc.koready.common;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import java.util.List;
import java.util.Map;

/**
 * 오류 응답 본문 {@code {code, message, fields}}. 오류에 따라 더 주는 값(extra)은 같은 단계의 필드로 나간다
 * ({@code ACTIVE_TRIP_EXISTS}의 {@code activeTripId}).
 */
public record ErrorBody(String code, String message, List<Field> fields, @JsonAnyGetter Map<String, Object> extra) {

	/** 검증 실패한 입력 하나. reason은 개발 확인용이다 */
	public record Field(String field, String reason) {
	}
}
