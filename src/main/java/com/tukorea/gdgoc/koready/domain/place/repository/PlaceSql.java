package com.tukorea.gdgoc.koready.domain.place.repository;

/**
 * 장소를 찾는 SQL들이 같이 쓰는 조건. 장소 테이블 별칭은 {@code p}다.
 */
public final class PlaceSql {

	/** 제외 분류({@code place_classes.excluded})에 대·중·소분류 하나라도 걸린 장소를 뺀다 */
	public static final String NOT_EXCLUDED = """
			NOT EXISTS (SELECT 1 FROM place_classes x
			                WHERE x.excluded AND x.code IN (p.class1_code, p.class2_code, p.class3_code))""";

	/**
	 * 장소 후보 범위: 사라진 장소, 숙박, 제외 분류, 여행 기간({@code :startDate}~{@code :endDate})과 겹치지 않는 축제·행사를 뺀다.
	 * 추천 목록·장소 검색과 동선 만들기가 같이 쓴다 (검색으로 담은 장소가 동선 만들기에서 거절되지 않게).
	 * 어노테이션 쿼리에도 붙일 수 있게 상수식으로 둔다.
	 */
	public static final String CANDIDATE_SCOPE = "p.active\n"
			+ "    AND p.class1_code <> 'AC'\n"
			+ "    AND " + NOT_EXCLUDED + "\n"
			+ "    AND (p.event_end_date IS NULL OR p.event_end_date >= :startDate)\n"
			+ "    AND (p.event_start_date IS NULL OR p.event_start_date <= :endDate)\n";

	private PlaceSql() {
	}
}
