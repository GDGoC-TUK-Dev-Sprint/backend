package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.trip.config.RouteProperties;
import com.tukorea.gdgoc.koready.domain.trip.entity.Leg;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripDay;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripItem;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 일자 하나의 이동 구간과 도착·출발 시각을 다시 계산한다 (순서 변경·장소 삭제).
 * 숙소 출발 → 구간 이동시간 → 도착 → 체류 → 출발 → … → 숙소 도착 순서로 이어 붙인다.
 */
@Component
@RequiredArgsConstructor
class DayRecalculator {

	private final LegRouter legRouter;
	private final RouteProperties properties;

	/**
	 * 항목이 seq 순서로 정렬돼 있고 기존 구간은 지워져 있어야 한다 (부르는 쪽이 비운 뒤 flush한다).
	 * 항목이 없으면 구간을 만들지 않고 숙소 출발·도착 시각을 비운다.
	 */
	void rebuild(TripDay day, LatLng stay) {
		List<TripItem> items = day.getItems();
		if (items.isEmpty()) {
			day.updateTimes(null, null);
			return;
		}
		LocalTime start = day.getDepartTime() != null ? day.getDepartTime() : properties.dayStart();
		LocalTime cursor = start;
		TripItem previous = null;
		int legSeq = 0;
		for (TripItem item : items) {
			LegRoute route = route(previous, item, stay);
			day.addLeg(newLeg(day, legSeq++, previous, item, route));
			LocalTime arrive = cursor.plusMinutes(route.durationMinutes());
			LocalTime depart = arrive.plusMinutes(item.getStayMinutes());
			item.schedule(arrive, depart);
			cursor = depart;
			previous = item;
		}
		LegRoute back = route(previous, null, stay);
		day.addLeg(newLeg(day, legSeq, previous, null, back));
		day.updateTimes(start, cursor.plusMinutes(back.durationMinutes()));
	}

	private LegRoute route(TripItem from, TripItem to, LatLng stay) {
		return legRouter.route(locationOf(from, stay), locationOf(to, stay));
	}

	/** 항목이 null이면 숙소다 */
	private static LatLng locationOf(TripItem item, LatLng stay) {
		return item == null ? stay : LatLng.from(item.getPlace().getLocation());
	}

	private static Leg newLeg(TripDay day, int seq, TripItem from, TripItem to, LegRoute route) {
		return Leg.create(day, seq, from, to, route.mode(), route.distanceM(), route.durationMinutes(),
				route.fareKrw(), route.estimated());
	}
}
