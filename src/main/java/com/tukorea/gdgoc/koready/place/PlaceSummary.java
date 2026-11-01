package com.tukorea.gdgoc.koready.place;

import com.tukorea.gdgoc.koready.common.Code;
import com.tukorea.gdgoc.koready.common.LatLng;
import java.util.Locale;

/**
 * 목록·카드·지도에 쓰는 장소 요약. 카드 분류는 "대분류 · 소분류"다.
 *
 * @param name 요청 언어 이름 (영문이 없으면 국문)
 * @param category 대분류
 * @param classification 소분류
 * @param image 사진이 없으면 null
 */
public record PlaceSummary(Long placeId, String name, String nameKo, Code category, Code classification, Image image,
		LatLng location) {

	/**
	 * TourAPI 사진. {@code license}가 {@code TYPE3}이면 변경 금지다 (자르거나 글자·필터를 합성하지 않는다).
	 */
	public record Image(String url, String thumbnailUrl, String license) {

		/** TourAPI 저작권 유형(Type1·Type3)을 응답 값(TYPE1·TYPE3)으로 바꾼다. 사진이 없으면 null */
		public static Image of(String url, String thumbnailUrl, String license) {
			if (url == null) {
				return null;
			}
			return new Image(url, thumbnailUrl, license == null ? null : license.toUpperCase(Locale.ROOT));
		}
	}
}
