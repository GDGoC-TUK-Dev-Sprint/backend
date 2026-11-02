package com.tukorea.gdgoc.koready.domain.geo.dto;

import java.util.List;

/**
 * 주소·장소명 검색 결과. 주소 결과(최대 5개) 다음에 장소 결과(최대 5개)가 온다. 페이지는 없다.
 */
public record GeoSearchResponse(List<GeoSearchItem> items) {
}
