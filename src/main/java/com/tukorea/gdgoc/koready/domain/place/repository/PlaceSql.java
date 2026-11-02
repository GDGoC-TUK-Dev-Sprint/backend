package com.tukorea.gdgoc.koready.domain.place.repository;

/**
 * 장소를 찾는 SQL들이 같이 쓰는 조건. 장소 테이블 별칭은 {@code p}다.
 */
public final class PlaceSql {

	/** 제외 분류({@code place_classes.excluded})에 대·중·소분류 하나라도 걸린 장소를 뺀다 */
	public static final String NOT_EXCLUDED = """
			NOT EXISTS (SELECT 1 FROM place_classes x
			                WHERE x.excluded AND x.code IN (p.class1_code, p.class2_code, p.class3_code))""";

	private PlaceSql() {
	}
}
