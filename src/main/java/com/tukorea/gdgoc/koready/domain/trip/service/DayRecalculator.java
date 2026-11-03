package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.place.repository.PlaceOpeningHourRepository;
import com.tukorea.gdgoc.koready.domain.trip.config.RouteProperties;
import com.tukorea.gdgoc.koready.domain.trip.entity.Leg;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripDay;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripItem;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 일자 하나의 이동 구간과 도착·출발 시각을 계산한다 (동선 만들기·순서 변경·장소 삭제).
 * 숙소 출발 → 구간 이동시간 → 도착 → 체류 → 출발 → … → 숙소 도착 순서로 이어 붙인다.
 * 여는 시각 전에 도착하면 여는 시각까지 기다린다 (도착 시각 = 여는 시각, {@link OpeningHours#earliestStart}).
 */
@Component
@RequiredArgsConstructor
class DayRecalculator {

	private final LegRouter legRouter;
	private final RouteProperties properties;
	private final PlaceOpeningHourRepository openingHours;

	/**
	 * 항목이 seq 순서로 정렬돼 있고 기존 구간은 지워져 있어야 한다 (부르는 쪽이 비운 뒤 flush한다).
	 * 항목이 없으면 구간을 만들지 않고 숙소 출발·도착 시각을 비운다.
	 */
	void rebuild(TripDay day, LatLng stay) {
		rebuild(day, stay, OpeningHours.byPlace(day.getItems().stream().map(TripItem::getPlace).toList(), openingHours));
	}

	/** 장소마다 운영시간을 이미 읽어 둔 경우 (동선 만들기) */
	void rebuild(TripDay day, LatLng stay, Map<Long, OpeningHours> hoursByPlace) {
		List<TripItem> items = day.getItems();
		if (items.isEmpty()) {
			day.updateTimes(null, null);
			return;
		}
		LocalTime start = day.getDepartTime() != null ? day.getDepartTime() : properties.dayStart();
		int cursor = OpeningHours.minutes(start);
		TripItem previous = null;
		int legSeq = 0;
		for (TripItem item : items) {
			LegRoute route = route(previous, item, stay);
			day.addLeg(newLeg(day, legSeq++, previous, item, route));
			int arrive = waitForOpening(day.getTripDate(), cursor + route.durationMinutes(), item,
					hoursByPlace.getOrDefault(item.getPlace().getId(), OpeningHours.UNRESTRICTED));
			int depart = arrive + item.getStayMinutes();
			item.schedule(clock(arrive), clock(depart));
			cursor = depart;
			previous = item;
		}
		LegRoute back = route(previous, null, stay);
		day.addLeg(newLeg(day, legSeq, previous, null, back));
		day.updateTimes(start, clock(cursor + back.durationMinutes()));
	}

	/** 기다려서 머물 수 있으면 여는 시각, 아니면 도착 시각 그대로 (경고는 조회할 때 붙는다). 자정을 넘긴 도착은 기다리지 않는다 */
	private static int waitForOpening(LocalDate date, int arrive, TripItem item, OpeningHours hours) {
		if (arrive >= OpeningHours.MINUTES_PER_DAY) {
			return arrive;
		}
		Integer start = hours.earliestStart(date, arrive, item.getStayMinutes());
		return start == null ? arrive : start;
	}

	/** 그날 0시부터의 분 → 시각. 자정을 넘기면 다음 날 시각으로 돈다 */
	private static LocalTime clock(int minutes) {
		return LocalTime.MIDNIGHT.plusMinutes(minutes);
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
