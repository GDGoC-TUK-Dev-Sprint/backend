package com.tukorea.gdgoc.koready.domain.geo.dto;

/**
 * 핀 위치 주소.
 *
 * @param address 도로명 주소, 없으면 지번 주소 (국문). 바다·국외처럼 주소가 없으면 null
 * @param inSeoul 서울 지원 지역인지 (법정동 코드 앞 두 자리가 11)
 */
public record GeoReverseResponse(String address, boolean inSeoul) {
}
