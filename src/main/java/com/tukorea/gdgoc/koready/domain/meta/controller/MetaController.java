package com.tukorea.gdgoc.koready.domain.meta.controller;

import com.tukorea.gdgoc.koready.domain.meta.dto.MetaOptions;
import com.tukorea.gdgoc.koready.domain.meta.service.MetaService;
import com.tukorea.gdgoc.koready.global.web.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class MetaController {

	private final MetaService metaService;

	/** 조건 입력 선택지: 국적·카테고리·지역 14개 */
	@GetMapping("/api/meta/options")
	MetaOptions options(Language language) {
		return metaService.options(language);
	}
}
