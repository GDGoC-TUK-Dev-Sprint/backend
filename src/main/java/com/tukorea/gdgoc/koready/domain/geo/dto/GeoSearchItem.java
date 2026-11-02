package com.tukorea.gdgoc.koready.domain.geo.dto;

import com.tukorea.gdgoc.koready.global.dto.LatLng;

/**
 * 주소·장소명 검색 결과 한 곳.
 *
 * @param type 주소(네이버 Geocoding)·장소(네이버 지역 검색)
 * @param label 목록에 보일 이름. 주소 결과는 주소, 장소 결과는 네이버 장소 이름 (국문)
 * @param address 주소. 주소 결과는 영어로 요청하면 영문 주소, 장소 결과는 국문 도로명(없으면 지번)
 * @param location 지도를 옮길 위치
 * @param inSeoul 서울 지원 지역인지
 * @param link 장소 결과의 네이버 원문 링크 (없으면 null). 주소 결과는 null
 */
public record GeoSearchItem(Type type, String label, String address, LatLng location, boolean inSeoul, String link) {

	public enum Type {
		ADDRESS, PLACE
	}
}
