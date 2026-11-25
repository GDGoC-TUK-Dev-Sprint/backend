package com.tukorea.gdgoc.koready.domain.favorite.controller;

import com.tukorea.gdgoc.koready.domain.favorite.service.FavoriteService;
import com.tukorea.gdgoc.koready.global.auth.LoginUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class FavoriteController {

	private final FavoriteService favoriteService;

	/** 찜하기: 장소 상세 하트. 이미 찜했어도 204 */
	@PutMapping("/api/favorites/{placeId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void add(@LoginUserId Long userId, @PathVariable Long placeId) {
		favoriteService.add(userId, placeId);
	}

	/** 찜 풀기: 장소 상세·마이페이지 하트. 찜하지 않았어도 204 */
	@DeleteMapping("/api/favorites/{placeId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@LoginUserId Long userId, @PathVariable Long placeId) {
		favoriteService.remove(userId, placeId);
	}
}
