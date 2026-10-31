package com.tukorea.gdgoc.koready.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 서비스에서 입력을 검사할 때 실패한 입력을 모은다. 다 모은 뒤 {@link #throwIfAny()}로 한 번에 {@code 400 INVALID_REQUEST}를 던진다.
 */
public class FieldErrors {

	private final List<ErrorBody.Field> fields = new ArrayList<>();

	public FieldErrors add(String field, String reason) {
		fields.add(new ErrorBody.Field(field, reason));
		return this;
	}

	public boolean has(String field) {
		return fields.stream().anyMatch(f -> f.field().equals(field));
	}

	public void throwIfAny() {
		if (!fields.isEmpty()) {
			throw ApiException.invalid(fields);
		}
	}
}
