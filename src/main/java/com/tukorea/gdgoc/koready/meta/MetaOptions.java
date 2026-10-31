package com.tukorea.gdgoc.koready.meta;

import com.tukorea.gdgoc.koready.common.Code;
import com.tukorea.gdgoc.koready.common.LatLng;
import java.util.List;

/**
 * 조건 입력 선택지. 이름은 요청 언어다.
 */
record MetaOptions(List<Code> nationalities, List<Code> categories, List<AreaOption> areas) {

	record AreaOption(String code, String name, LatLng center) {
	}
}
