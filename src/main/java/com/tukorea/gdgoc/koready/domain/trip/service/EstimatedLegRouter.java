package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.trip.config.RouteProperties;
import com.tukorea.gdgoc.koready.domain.trip.entity.LegMode;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 좌표로 추정한 이동 구간 ({@code estimated = true}). 직선거리가 도보 한계 이하면 도보(운임 0),
 * 넘으면 대중교통(운임은 모르므로 null)이다.
 */
@Component
@RequiredArgsConstructor
class EstimatedLegRouter implements LegRouter {

	private static final double EARTH_RADIUS_M = 6_371_000;

	private final RouteProperties properties;

	@Override
	public LegRoute route(LatLng from, LatLng to) {
		double straight = distanceMeters(from, to);
		int distance = (int) Math.round(straight * properties.detourFactor());
		if (straight <= properties.walkMaxDistanceM()) {
			int minutes = atLeastOne(distance / properties.walkMetersPerMinute());
			return new LegRoute(LegMode.WALK, distance, minutes, 0, true);
		}
		int minutes = atLeastOne(distance / properties.transitMetersPerMinute()) + properties.transitWaitMinutes();
		return new LegRoute(LegMode.TRANSIT, distance, minutes, null, true);
	}

	private static int atLeastOne(double minutes) {
		return Math.max(1, (int) Math.ceil(minutes));
	}

	/** 두 점의 직선(대권) 거리(m). 하버사인 공식. 동선 만들기의 일자 배정도 이 거리로 묶는다 */
	static double distanceMeters(LatLng a, LatLng b) {
		double dLat = Math.toRadians(b.lat() - a.lat());
		double dLng = Math.toRadians(b.lng() - a.lng());
		double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
						* Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
	}
}
