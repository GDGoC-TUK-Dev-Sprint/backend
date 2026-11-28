package com.tukorea.gdgoc.koready.domain.favorite.controller;

import com.tukorea.gdgoc.koready.domain.favorite.dto.FavoriteItem;
import com.tukorea.gdgoc.koready.domain.favorite.service.FavoriteListService;
import com.tukorea.gdgoc.koready.global.auth.LoginUserId;
import com.tukorea.gdgoc.koready.global.dto.PageQuery;
import com.tukorea.gdgoc.koready.global.dto.PageResponse;
import com.tukorea.gdgoc.koready.global.web.Language;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class FavoriteListController {

	private final FavoriteListService favoriteListService;

	/** 찜한 장소: 마이페이지 찜 개수(totalCount)와 미리보기. 최근 찜한 순. "전체보기" 화면이 생기면 같은 API를 넘겨 쓴다 */
	@GetMapping("/api/favorites")
	PageResponse<FavoriteItem> list(@LoginUserId Long userId, @Valid PageQuery pageQuery, Language language) {
		return favoriteListService.list(userId, pageQuery, language);
	}
}
