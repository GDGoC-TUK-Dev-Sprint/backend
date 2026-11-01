package com.tukorea.gdgoc.koready.candidate;

import com.tukorea.gdgoc.koready.common.PageQuery;
import com.tukorea.gdgoc.koready.meta.Category;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 장소 후보 쿼리. {@code size}는 기본 5다 (한 페이지 5곳).
 *
 * @param category 필터. 이 여행에서 고른 카테고리 중 하나이고, 없으면 전체
 * @param query 장소 이름 검색어. 있으면 카테고리 조건을 풀고 이름으로 찾는다. 길이(앞뒤 공백을 뺀 글자 수)는 서비스가 검사한다
 */
public record CandidateQuery(
		Category category,
		@Pattern(regexp = "(?s).*\\S.*", message = "공백만으로는 찾을 수 없습니다") String query,
		@PositiveOrZero Integer page,
		@Min(1) @Max(PageQuery.MAX_SIZE) Integer size) {

	public static final int DEFAULT_SIZE = 5;
	public static final int MAX_QUERY_LENGTH = 50;

	public CandidateQuery {
		if (page == null) {
			page = 0;
		}
		if (size == null) {
			size = DEFAULT_SIZE;
		}
	}

	/** 검색이면 앞뒤 공백을 뺀 검색어, 추천 목록이면 null */
	String searchText() {
		return query == null ? null : query.strip();
	}

	long offset() {
		return (long) page * size;
	}
}
