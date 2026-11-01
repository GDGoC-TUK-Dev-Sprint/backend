package com.tukorea.gdgoc.koready.domain.meta.dto;

import com.tukorea.gdgoc.koready.global.dto.Code;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import java.util.List;

/**
 * 조건 입력 선택지. 이름은 요청 언어다.
 */
public record MetaOptions(List<Code> nationalities, List<Code> categories, List<AreaOption> areas) {

	public record AreaOption(String code, String name, LatLng center) {
	}
}
