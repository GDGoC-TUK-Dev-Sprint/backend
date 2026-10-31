package com.tukorea.gdgoc.koready.trip;

import com.tukorea.gdgoc.koready.common.Language;
import com.tukorea.gdgoc.koready.common.LoginUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class TripController {

	private final TripService tripService;

	/** 여행 만들기: 조건 입력의 "이 조건으로 여행 계획 생성하기" */
	@PostMapping("/api/trips")
	@ResponseStatus(HttpStatus.CREATED)
	TripResponse create(@LoginUserId Long userId, @Valid @RequestBody TripRequest request, Language language) {
		return tripService.create(userId, request, language);
	}

	/** 여행 요약: 장소 선택의 조건 요약, 일자별 동선·지도·계획 확인의 여행 정보 */
	@GetMapping("/api/trips/{tripId}")
	TripResponse get(@LoginUserId Long userId, @PathVariable Long tripId, Language language) {
		return tripService.get(userId, tripId, language);
	}
}
