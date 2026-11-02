package com.tukorea.gdgoc.koready.infra.naver;

/**
 * 네이버 Geocoding 주소 결과 한 곳. 주소가 비어 있으면 null이다.
 *
 * @param roadAddress 도로명 주소 (국문, 건물 이름 포함)
 * @param jibunAddress 지번 주소 (국문)
 * @param englishAddress 영문 주소
 * @param sido 시도 이름 (국문, 예: 서울특별시). Geocoding은 법정동 코드를 주지 않는다
 */
public record GeocodeAddress(String roadAddress, String jibunAddress, String englishAddress, String sido, double lat,
		double lng) {
}
