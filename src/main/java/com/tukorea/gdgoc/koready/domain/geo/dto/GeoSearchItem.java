package com.tukorea.gdgoc.koready.domain.geo.dto;

import com.tukorea.gdgoc.koready.global.dto.LatLng;

/**
 * 주소·장소명 검색 결과 한 곳.
 *
 * @param type 주소(네이버 Geocoding)·장소(우리 장소 DB, 숙박 포함)
 * @param label 목록에 보일 이름. 주소 결과는 주소, 장소 결과는 장소 이름 (요청 언어, 영문이 없으면 국문)
 * @param address 주소. 주소 결과는 영어로 요청하면 영문 주소, 장소 결과는 국문
 * @param location 지도를 옮길 위치
 * @param inSeoul 서울 지원 지역인지
 */
public record GeoSearchItem(Type type, String label, String address, LatLng location, boolean inSeoul) {

	public enum Type {
		ADDRESS, PLACE
	}
}
