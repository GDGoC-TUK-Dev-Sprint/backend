package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.global.dto.LatLng;
import java.util.List;

/**
 * 두 지점 사이 이동시간·운임을 구한다. 기본 구현은 {@link OdsayLegRouter}(ODsay, 받지 못하면 좌표 추정값)이고,
 * 방문 순서를 정할 때는 경로 API를 부르지 않도록 {@link EstimatedLegRouter}를 바로 쓴다.
 */
public interface LegRouter {

	LegRoute route(LatLng from, LatLng to);

	/** 여러 구간을 한 번에 구한다 (결과는 요청 순서). 경로 API를 쓰는 구현은 동시에 부르고 전체 시간 제한을 둔다 */
	default List<LegRoute> routeAll(List<Endpoints> legs) {
		return legs.stream().map(leg -> route(leg.from(), leg.to())).toList();
	}

	/** 구간 하나의 출발·도착 */
	record Endpoints(LatLng from, LatLng to) {
	}
}
