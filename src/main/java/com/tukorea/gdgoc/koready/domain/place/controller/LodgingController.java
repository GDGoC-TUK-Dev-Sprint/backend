package com.tukorea.gdgoc.koready.domain.place.controller;

import com.tukorea.gdgoc.koready.domain.place.dto.LodgingItem;
import com.tukorea.gdgoc.koready.domain.place.service.LodgingService;
import com.tukorea.gdgoc.koready.global.dto.PageQuery;
import com.tukorea.gdgoc.koready.global.dto.PageResponse;
import com.tukorea.gdgoc.koready.global.web.Language;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class LodgingController {

	private final LodgingService lodgingService;

	/** 숙소 위치 검색: 숙소 이름·지역으로 찾는다. 로그인 없이 부른다 */
	@GetMapping("/api/lodgings")
	PageResponse<LodgingItem> search(@RequestParam String query, @Valid PageQuery pageQuery, Language language) {
		return lodgingService.search(query, pageQuery, language);
	}
}
