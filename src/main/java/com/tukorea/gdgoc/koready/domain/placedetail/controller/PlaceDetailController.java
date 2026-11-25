package com.tukorea.gdgoc.koready.domain.placedetail.controller;

import com.tukorea.gdgoc.koready.domain.placedetail.dto.PlaceDetailResponse;
import com.tukorea.gdgoc.koready.domain.placedetail.service.PlaceDetailService;
import com.tukorea.gdgoc.koready.global.auth.LoginUserId;
import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.web.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class PlaceDetailController {

	private final PlaceDetailService placeDetailService;

	/** 장소 상세: 로그인하지 않아도 부르고, 로그인하면 일정 포함(tripId가 내 여행일 때)·찜 여부를 더 준다 */
	@GetMapping("/api/places/{placeId}")
	PlaceDetailResponse get(@LoginUserId(required = false) Long userId, @PathVariable Long placeId,
			@RequestParam(required = false) Long tripId, Language language, DisplayCurrency currency) {
		return placeDetailService.get(placeId, tripId, userId, language, currency);
	}
}
