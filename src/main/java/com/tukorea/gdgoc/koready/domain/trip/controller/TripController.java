package com.tukorea.gdgoc.koready.domain.trip.controller;

import com.tukorea.gdgoc.koready.domain.trip.dto.TripListItem;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripPeriod;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripRequest;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripResponse;
import com.tukorea.gdgoc.koready.domain.trip.service.TripListService;
import com.tukorea.gdgoc.koready.domain.trip.service.TripService;
import com.tukorea.gdgoc.koready.global.auth.LoginUserId;
import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.dto.PageQuery;
import com.tukorea.gdgoc.koready.global.dto.PageResponse;
import com.tukorea.gdgoc.koready.global.web.Language;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class TripController {

	private final TripService tripService;
	private final TripListService tripListService;

	/** 여행 만들기: 조건 입력의 "이 조건으로 여행 계획 생성하기" */
	@PostMapping("/api/trips")
	@ResponseStatus(HttpStatus.CREATED)
	TripResponse create(@LoginUserId Long userId, @Valid @RequestBody TripRequest request, Language language,
			DisplayCurrency currency) {
		return tripService.create(userId, request, language, currency);
	}

	/**
	 * 여행 목록 (확정한 여행만): {@code CURRENT}는 메인의 여행 티켓과 지도·계획 확인 탭, {@code PAST}는 마이페이지 이전 여행 기록
	 */
	@GetMapping("/api/trips")
	PageResponse<TripListItem> list(@LoginUserId Long userId, @RequestParam(defaultValue = "CURRENT") TripPeriod period,
			@Valid PageQuery pageQuery, Language language, DisplayCurrency currency) {
		return tripListService.list(userId, period, pageQuery, language, currency);
	}

	/** 여행 요약: 장소 선택의 조건 요약, 일자별 동선·지도·계획 확인의 여행 정보 */
	@GetMapping("/api/trips/{tripId}")
	TripResponse get(@LoginUserId Long userId, @PathVariable Long tripId, Language language,
			DisplayCurrency currency) {
		return tripService.get(userId, tripId, language, currency);
	}

	/** 조건 다시 저장: 장소 선택의 조건 요약 "변경"으로 조건 입력에 돌아가 다시 "이 조건으로 여행 계획 생성하기" */
	@PutMapping("/api/trips/{tripId}")
	TripResponse update(@LoginUserId Long userId, @PathVariable Long tripId, @Valid @RequestBody TripRequest request,
			Language language, DisplayCurrency currency) {
		return tripService.update(userId, tripId, request, language, currency);
	}

	/** 여행 삭제: 메인 여행 티켓의 X. 확인 창은 앱에서 띄운다 */
	@DeleteMapping("/api/trips/{tripId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@LoginUserId Long userId, @PathVariable Long tripId) {
		tripService.delete(userId, tripId);
	}
}
