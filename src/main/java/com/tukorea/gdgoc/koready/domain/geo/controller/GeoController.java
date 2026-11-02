package com.tukorea.gdgoc.koready.domain.geo.controller;

import com.tukorea.gdgoc.koready.domain.geo.dto.GeoReverseResponse;
import com.tukorea.gdgoc.koready.domain.geo.dto.GeoSearchResponse;
import com.tukorea.gdgoc.koready.domain.geo.service.GeoService;
import com.tukorea.gdgoc.koready.global.web.Language;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class GeoController {

	private final GeoService geoService;

	/** 지도에서 숙소 위치 선택의 주소·장소명 검색. 로그인 없이 부른다 */
	@GetMapping("/api/geo/search")
	GeoSearchResponse search(@RequestParam String query, Language language) {
		return geoService.search(query, language);
	}

	/** 지도 가운데 핀 위치의 주소와 서울 지원 지역인지. 로그인 없이 부른다 */
	@GetMapping("/api/geo/reverse")
	GeoReverseResponse reverse(@RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
			@RequestParam @DecimalMin("-180") @DecimalMax("180") double lng) {
		return geoService.reverse(lat, lng);
	}
}
