package com.tukorea.gdgoc.koready.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;

/**
 * 모든 오류를 {@code {code, message, fields}} 본문으로 바꾼다. 입력 검증 실패는 {@code 400 INVALID_REQUEST}와 {@code fields[]}다.
 */
@Slf4j
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String BAD_FORMAT = "형식이 맞지 않습니다";

	@ExceptionHandler(ApiException.class)
	ResponseEntity<Object> handleApiException(ApiException e) {
		ErrorCode code = e.getErrorCode();
		if (code.getStatus().is5xxServerError()) {
			log.error("{}: {}", code, e.getMessage(), e);
		}
		return ResponseEntity.status(code.getStatus())
				.body(new ErrorBody(code.name(), e.getMessage(), e.getFields(), e.getExtra()));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception e) {
		log.error("처리하지 못한 오류", e);
		return error(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), List.of());
	}

	/** {@code @Valid} 요청 본문·쿼리 객체 */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		List<ErrorBody.Field> fields = new ArrayList<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fields.add(field(error));
		}
		for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
			fields.add(new ErrorBody.Field(error.getObjectName(), error.getDefaultMessage()));
		}
		return invalid(fields);
	}

	/** 컨트롤러 파라미터에 바로 붙인 제약 ({@code @RequestParam @Max(50) int size} 등) */
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<ErrorBody.Field> fields = new ArrayList<>();
		for (ParameterValidationResult result : ex.getParameterValidationResults()) {
			if (result instanceof ParameterErrors errors) {
				errors.getFieldErrors().forEach(error -> fields.add(field(error)));
				continue;
			}
			String name = parameterName(result.getMethodParameter());
			for (MessageSourceResolvable error : result.getResolvableErrors()) {
				fields.add(new ErrorBody.Field(name, error.getDefaultMessage()));
			}
		}
		return invalid(fields);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String name = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : ex.getPropertyName();
		return invalid(List.of(new ErrorBody.Field(name, BAD_FORMAT)));
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return invalid(List.of(new ErrorBody.Field(ex.getParameterName(), "필수입니다")));
	}

	/** JSON 문법 오류, 타입·날짜·enum 값이 틀린 본문. 어느 필드인지 알 수 있으면 fields에 넣는다 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String path = jsonPath(ex);
		List<ErrorBody.Field> fields = path == null ? List.of() : List.of(new ErrorBody.Field(path, BAD_FORMAT));
		return error(ErrorCode.INVALID_REQUEST, "본문을 읽을 수 없습니다", fields);
	}

	/** 그 밖의 Spring MVC 오류 (없는 주소 404, 허용하지 않는 메서드 405 등). 상태는 그대로 두고 code는 상태 이름이다 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		String code;
		if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
			code = ErrorCode.INVALID_REQUEST.name();
		} else if (statusCode.value() == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
			code = ErrorCode.INTERNAL_ERROR.name();
		} else {
			HttpStatus known = HttpStatus.resolve(statusCode.value());
			code = known != null ? known.name() : "HTTP_" + statusCode.value();
		}
		if (statusCode.is5xxServerError()) {
			log.error("{}", code, ex);
		}
		return ResponseEntity.status(statusCode).headers(headers)
				.body(new ErrorBody(code, ex.getMessage(), List.of(), Map.of()));
	}

	private static ResponseEntity<Object> invalid(List<ErrorBody.Field> fields) {
		return error(ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_REQUEST.getMessage(), fields);
	}

	private static ResponseEntity<Object> error(ErrorCode code, String message, List<ErrorBody.Field> fields) {
		return ResponseEntity.status(code.getStatus()).body(new ErrorBody(code.name(), message, fields, Map.of()));
	}

	private static ErrorBody.Field field(FieldError error) {
		return new ErrorBody.Field(error.getField(), error.isBindingFailure() ? BAD_FORMAT : error.getDefaultMessage());
	}

	private static String parameterName(MethodParameter parameter) {
		RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
		if (param != null && !(param.name() + param.value()).isEmpty()) {
			return param.name().isEmpty() ? param.value() : param.name();
		}
		RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
		if (header != null && !(header.name() + header.value()).isEmpty()) {
			return header.name().isEmpty() ? header.value() : header.name();
		}
		return parameter.getParameterName();
	}

	/** Jackson 오류에서 필드 경로를 꺼낸다 ({@code stay.type}, {@code categories[1]}). 문법 오류처럼 경로가 없으면 null */
	private static String jsonPath(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
				StringBuilder path = new StringBuilder();
				for (JacksonException.Reference reference : jackson.getPath()) {
					if (reference.getPropertyName() != null) {
						if (!path.isEmpty()) {
							path.append('.');
						}
						path.append(reference.getPropertyName());
					} else if (reference.getIndex() >= 0) {
						path.append('[').append(reference.getIndex()).append(']');
					}
				}
				return path.toString();
			}
		}
		return null;
	}
}
