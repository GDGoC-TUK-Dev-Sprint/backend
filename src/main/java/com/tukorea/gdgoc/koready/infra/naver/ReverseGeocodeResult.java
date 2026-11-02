package com.tukorea.gdgoc.koready.infra.naver;

/**
 * 네이버 Reverse Geocoding 결과. 결과가 없거나(바다·국외) 그 주소가 없으면 null이다.
 *
 * @param legalCode 법정동 코드 10자리 (앞 두 자리가 시도, 서울은 11)
 * @param roadAddress 도로명 주소 (국문, 건물 이름 포함)
 * @param jibunAddress 지번 주소 (국문)
 */
public record ReverseGeocodeResult(String legalCode, String roadAddress, String jibunAddress) {
}
