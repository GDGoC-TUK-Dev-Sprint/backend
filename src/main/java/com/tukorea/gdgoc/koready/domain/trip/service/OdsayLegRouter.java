package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.trip.entity.LegMode;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.infra.odsay.OdsayClient;
import com.tukorea.gdgoc.koready.infra.odsay.OdsayRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 저장하는 이동 구간의 기본 계산 (동선 만들기·순서 변경·장소 삭제). 직선거리가 도보 한계 이하면 좌표 추정 도보이고(운임 0.
 * ODsay를 부르지 않는다. ODsay는 700m 안을 거절한다), 넘으면 ODsay 대중교통 경로의 소요시간·거리·운임이다 ({@code estimated = false}).
 * 키가 없거나 ODsay가 실패하면 좌표 추정값(운임 null)을 쓴다. 구간을 받지 못해도 동선은 만든다.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
class OdsayLegRouter implements LegRouter {

	private final EstimatedLegRouter estimatedLegRouter;
	private final OdsayClient odsay;

	@Override
	public LegRoute route(LatLng from, LatLng to) {
		LegRoute estimate = estimatedLegRouter.route(from, to);
		if (estimate.mode() == LegMode.WALK || !odsay.enabled()) {
			return estimate;
		}
		try {
			OdsayRoute route = odsay.searchPath(from, to);
			return new LegRoute(LegMode.TRANSIT, route.distanceM(), route.durationMinutes(), route.fareKrw(), false);
		} catch (ApiException e) {
			log.warn("이동 구간을 좌표 추정값으로 대신합니다 ({})", e.getMessage());
			return estimate;
		}
	}
}
