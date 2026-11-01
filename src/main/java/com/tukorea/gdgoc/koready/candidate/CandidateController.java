package com.tukorea.gdgoc.koready.candidate;

import com.tukorea.gdgoc.koready.common.Language;
import com.tukorea.gdgoc.koready.common.LoginUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class CandidateController {

	private final CandidateService candidateService;

	/** 장소 선택의 추천 장소 목록 (필터·페이지 바)과 장소 이름 검색 */
	@GetMapping("/api/trips/{tripId}/candidates")
	CandidatePage find(@LoginUserId Long userId, @PathVariable Long tripId, @Valid CandidateQuery query,
			Language language) {
		return candidateService.find(userId, tripId, query, language);
	}
}
