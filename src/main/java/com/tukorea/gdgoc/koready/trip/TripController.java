package com.tukorea.gdgoc.koready.trip;

import com.tukorea.gdgoc.koready.common.Language;
import com.tukorea.gdgoc.koready.common.LoginUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
}
