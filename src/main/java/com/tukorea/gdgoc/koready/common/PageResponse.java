package com.tukorea.gdgoc.koready.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * 목록 응답 {@code items·page·size·totalCount·hasNext}. Spring {@link Page}를 그대로 내보내면 필드가 다르다.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalCount, boolean hasNext) {

	public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalCount) {
		return new PageResponse<>(items, page, size, totalCount, (long) (page + 1) * size < totalCount);
	}

	public static <T> PageResponse<T> of(List<T> items, PageQuery query, long totalCount) {
		return of(items, query.page(), query.size(), totalCount);
	}

	public static <E, T> PageResponse<T> from(Page<E> page, Function<? super E, ? extends T> mapper) {
		List<T> items = page.getContent().stream().<T>map(mapper).toList();
		return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.hasNext());
	}
}
