package com.tukorea.gdgoc.koready.infra.naver;

/**
 * 네이버 지역 검색 결과 한 곳. 비어 있는 값은 null이다.
 *
 * @param name 업체·기관 이름 (강조 태그를 빼고 HTML 문자 표기를 글자로 바꾼 것, 국문)
 * @param link 네이버가 주는 원문 링크 (업체 홈페이지 등). 받은 그대로다
 * @param roadAddress 도로명 주소 (국문)
 * @param jibunAddress 지번 주소 (국문)
 */
public record LocalSearchPlace(String name, String link, String roadAddress, String jibunAddress, double lat,
		double lng) {
}
