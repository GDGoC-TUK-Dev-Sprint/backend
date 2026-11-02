package com.tukorea.gdgoc.koready.global.exception;

import java.util.List;
import java.util.Map;
import lombok.Getter;

/**
 * 서비스에서 던지는 오류. {@link GlobalExceptionHandler}가 {@code {code, message, fields}} 응답으로 바꾼다.
 */
@Getter
public class ApiException extends RuntimeException {

	private final ErrorCode errorCode;
	private final List<ErrorBody.Field> fields;
	private final Map<String, Object> extra;

	public ApiException(ErrorCode errorCode) {
		this(errorCode, errorCode.getMessage());
	}

	public ApiException(ErrorCode errorCode, String message) {
		this(errorCode, message, List.of(), Map.of());
	}

	/** 오류 본문에 값을 더 준다 (예: {@code Map.of("activeTripId", id)}) */
	public ApiException(ErrorCode errorCode, Map<String, Object> extra) {
		this(errorCode, errorCode.getMessage(), List.of(), extra);
	}

	/** 외부 API 실패처럼 원인 예외가 있는 오류. 원인은 서버 로그에만 남고 응답에는 나가지 않는다 */
	public ApiException(ErrorCode errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
		this.fields = List.of();
		this.extra = Map.of();
	}

	private ApiException(ErrorCode errorCode, String message, List<ErrorBody.Field> fields, Map<String, Object> extra) {
		super(message);
		this.errorCode = errorCode;
		this.fields = List.copyOf(fields);
		this.extra = Map.copyOf(extra);
	}

	/** {@code 400 INVALID_REQUEST}와 검증 실패한 입력들 */
	public static ApiException invalid(List<ErrorBody.Field> fields) {
		return new ApiException(ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_REQUEST.getMessage(), fields, Map.of());
	}
}
