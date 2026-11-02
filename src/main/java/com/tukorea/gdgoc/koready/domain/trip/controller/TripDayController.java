package com.tukorea.gdgoc.koready.domain.trip.controller;

import com.tukorea.gdgoc.koready.domain.trip.dto.ReorderRequest;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripDayResponse;
import com.tukorea.gdgoc.koready.domain.trip.service.TripDayService;
import com.tukorea.gdgoc.koready.global.auth.LoginUserId;
import com.tukorea.gdgoc.koready.global.web.Language;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class TripDayController {

	private final TripDayService tripDayService;

	/** 일자별 일정: 일자별 동선·지도로 보기·계획 확인 */
	@GetMapping("/api/trips/{tripId}/days/{dayNumber}")
	TripDayResponse get(@LoginUserId Long userId, @PathVariable Long tripId, @PathVariable int dayNumber,
			Language language) {
		return tripDayService.getDay(userId, tripId, dayNumber, language);
	}

	/** 순서 변경: 그 일자의 항목 ID 전부를 새 순서로 보낸다. 바뀐 일자 전체를 돌려준다 */
	@PutMapping("/api/trips/{tripId}/days/{dayNumber}/order")
	TripDayResponse reorder(@LoginUserId Long userId, @PathVariable Long tripId, @PathVariable int dayNumber,
			@Valid @RequestBody ReorderRequest request, Language language) {
		return tripDayService.reorder(userId, tripId, dayNumber, request.itemIds(), language);
	}

	/** 장소 삭제: 그 항목이 속한 일자 전체를 돌려준다 */
	@DeleteMapping("/api/trips/{tripId}/items/{itemId}")
	TripDayResponse deleteItem(@LoginUserId Long userId, @PathVariable Long tripId, @PathVariable Long itemId,
			Language language) {
		return tripDayService.deleteItem(userId, tripId, itemId, language);
	}
}
