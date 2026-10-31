package com.tukorea.gdgoc.koready.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 목록 쿼리 {@code page}(0부터)·{@code size}(기본 20, 최대 50). 컨트롤러에서 {@code @Valid PageQuery pageQuery}로 받는다.
 * 기본 크기가 다른 목록은 자기 쿼리 레코드에 같은 제약({@link #MAX_SIZE})을 둔다.
 */
public record PageQuery(@PositiveOrZero Integer page, @Min(1) @Max(MAX_SIZE) Integer size) {

	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 50;

	public PageQuery {
		if (page == null) {
			page = 0;
		}
		if (size == null) {
			size = DEFAULT_SIZE;
		}
	}

	public Pageable toPageable() {
		return PageRequest.of(page, size);
	}

	/** JdbcTemplate 쿼리의 OFFSET */
	public long offset() {
		return (long) page * size;
	}
}
