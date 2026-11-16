package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.global.dto.LatLng;

/**
 * 두 지점 사이 이동시간·운임을 구한다. 기본 구현은 {@link OdsayLegRouter}(ODsay, 받지 못하면 좌표 추정값)이고,
 * 방문 순서를 정할 때는 경로 API를 부르지 않도록 {@link EstimatedLegRouter}를 바로 쓴다.
 */
public interface LegRouter {

	LegRoute route(LatLng from, LatLng to);
}
