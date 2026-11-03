package com.tukorea.gdgoc.koready.domain.trip.controller;

import com.tukorea.gdgoc.koready.domain.trip.dto.RouteRequest;
import com.tukorea.gdgoc.koready.domain.trip.dto.RouteResponse;
import com.tukorea.gdgoc.koready.domain.trip.service.RouteService;
import com.tukorea.gdgoc.koready.global.auth.LoginUserId;
import com.tukorea.gdgoc.koready.global.web.Language;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class TripRouteController {

	private final RouteService routeService;

	/** 동선 만들기: 장소 선택의 "N곳으로 동선 만들기". 동선이 이미 있으면 지우고 새로 만든다 */
	@PostMapping("/api/trips/{tripId}/route")
	@ResponseStatus(HttpStatus.CREATED)
	RouteResponse create(@LoginUserId Long userId, @PathVariable Long tripId, @Valid @RequestBody RouteRequest request,
			Language language) {
		return routeService.create(userId, tripId, request.placeIds(), language);
	}
}
