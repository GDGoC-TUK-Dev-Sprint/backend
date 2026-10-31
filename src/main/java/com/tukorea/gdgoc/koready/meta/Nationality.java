package com.tukorea.gdgoc.koready.meta;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 조건 입력의 국적. 코드는 ISO 3166-1 alpha-2이고, 국적별 카드 소비 통계가 있는 나라만 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum Nationality {

	CN("중국", "China"),
	JP("일본", "Japan"),
	US("미국", "United States");

	private final String nameKo;
	private final String nameEn;
}
