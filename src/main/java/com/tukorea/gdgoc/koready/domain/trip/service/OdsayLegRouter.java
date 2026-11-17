package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.trip.entity.LegMode;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.infra.odsay.OdsayClient;
import com.tukorea.gdgoc.koready.infra.odsay.OdsayRoute;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 저장하는 이동 구간의 기본 계산 (동선 만들기·순서 변경·장소 삭제). 직선거리가 도보 한계 이하면 좌표 추정 도보이고(운임 0.
 * ODsay를 부르지 않는다. ODsay는 700m 안을 거절한다), 넘으면 ODsay 대중교통 경로의 소요시간·거리·운임이다 ({@code estimated = false}).
 * 키가 없거나 ODsay가 실패하거나 시간 제한 안에 받지 못하면 좌표 추정값(운임 null)을 쓴다. 구간을 받지 못해도 동선은 만든다.
 */
@Primary
@Component
@RequiredArgsConstructor
class OdsayLegRouter implements LegRouter {

	private final EstimatedLegRouter estimatedLegRouter;
	private final OdsayClient odsay;

	@Override
	public LegRoute route(LatLng from, LatLng to) {
		return routeAll(List.of(new Endpoints(from, to))).getFirst();
	}

	/** 대중교통 거리인 구간만 모아 ODsay를 한 번에 부른다 ({@link OdsayClient#searchPaths}) */
	@Override
	public List<LegRoute> routeAll(List<Endpoints> legs) {
		List<LegRoute> routes = new ArrayList<>(estimatedLegRouter.routeAll(legs));
		List<Integer> transit = new ArrayList<>();
		for (int i = 0; i < routes.size(); i++) {
			if (routes.get(i).mode() != LegMode.WALK) {
				transit.add(i);
			}
		}
		if (transit.isEmpty() || !odsay.enabled()) {
			return routes;
		}
		List<OdsayClient.Endpoints> requests = transit.stream()
				.map(i -> new OdsayClient.Endpoints(legs.get(i).from(), legs.get(i).to()))
				.toList();
		List<Optional<OdsayRoute>> found = odsay.searchPaths(requests);
		for (int k = 0; k < transit.size(); k++) {
			int index = transit.get(k);
			found.get(k).ifPresent(route -> routes.set(index,
					new LegRoute(LegMode.TRANSIT, route.distanceM(), route.durationMinutes(), route.fareKrw(), false)));
		}
		return routes;
	}
}
