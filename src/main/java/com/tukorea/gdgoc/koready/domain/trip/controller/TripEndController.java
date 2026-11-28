package com.tukorea.gdgoc.koready.domain.trip.controller;

import com.tukorea.gdgoc.koready.domain.trip.service.TripEndService;
import com.tukorea.gdgoc.koready.global.auth.LoginUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class TripEndController {

	private final TripEndService tripEndService;

	/** 여행 종료: 마이페이지 "여행 종료하기". 이미 끝난 여행이어도 204 */
	@PostMapping("/api/trips/{tripId}/end")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void end(@LoginUserId Long userId, @PathVariable Long tripId) {
		tripEndService.end(userId, tripId);
	}
}
