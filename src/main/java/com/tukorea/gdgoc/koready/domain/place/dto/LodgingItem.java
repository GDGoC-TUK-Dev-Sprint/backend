package com.tukorea.gdgoc.koready.domain.place.dto;

import com.tukorea.gdgoc.koready.global.dto.Code;
import com.tukorea.gdgoc.koready.global.dto.LatLng;

/**
 * 숙소 검색 결과 한 곳.
 *
 * @param name 요청 언어 이름 (영문이 없으면 국문)
 * @param address 주소 (국문)
 * @param lodgingType 유형 배지 (숙박 소분류: 호텔·게스트하우스·한옥스테이 등)
 */
public record LodgingItem(Long placeId, String name, String nameKo, String address, Code lodgingType,
		LatLng location) {
}
