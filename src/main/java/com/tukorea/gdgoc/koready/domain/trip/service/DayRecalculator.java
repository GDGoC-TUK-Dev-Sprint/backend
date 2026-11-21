package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.place.repository.PlaceOpeningHourRepository;
import com.tukorea.gdgoc.koready.domain.trip.config.RouteProperties;
import com.tukorea.gdgoc.koready.domain.trip.entity.Leg;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripDay;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripItem;
import com.tukorea.gdgoc.koready.domain.trip.repository.RoutedLeg;
import com.tukorea.gdgoc.koready.domain.trip.repository.TripRepository;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 일자 하나의 이동 구간과 도착·출발 시각을 계산한다 (동선 만들기·순서 변경·장소 삭제).
 * 숙소 출발 → 구간 이동시간 → 도착 → 체류 → 출발 → … → 숙소 도착 순서로 이어 붙인다.
 * 여는 시각 전에 도착하면 여는 시각까지 기다린다 (도착 시각 = 여는 시각, {@link OpeningHours#earliestStart}).
 * 구간은 {@link LegRouter#routeAll}로 한 번에 받고, 지우기 전 구간 중 양 끝 장소가 같은 구간은 경로 API 값을 다시 쓴다
 * (동선 다시 만들기·순서 변경·장소 삭제. 경로 API는 출발 시각을 받지 않아 같은 장소 쌍이면 값이 같다).
 */
@Component
@RequiredArgsConstructor
class DayRecalculator {

	private final LegRouter legRouter;
	private final RouteProperties properties;
	private final PlaceOpeningHourRepository openingHours;

	/**
	 * 동선 만들기의 새 일자. 장소마다 운영시간을 이미 읽어 두었다. 지운 동선에서 모아 둔 구간({@link #routedLegs(List)})
	 * 중 양 끝 장소가 같은 구간은 경로 API를 다시 부르지 않고 쓴다. 여러 일자를 만들 때는 먼저 {@link #routeMissing}으로
	 * 모든 일자의 구간을 한 번에 받아 둔다 (경로 API 전체 시간 제한이 요청에 한 번 걸린다).
	 */
	void build(TripDay day, LatLng stay, Map<Long, OpeningHours> hoursByPlace, Map<Segment, LegRoute> routed) {
		calculate(day, stay, hoursByPlace, routed);
	}

	/**
	 * 순서 변경·장소 삭제. 항목이 seq 순서로 정렬돼 있고 기존 구간은 지워져 있어야 한다 (부르는 쪽이 비운 뒤 flush한다).
	 * 지우기 전에 {@link #routedLegs(TripDay)}로 모아 둔 구간 중 양 끝 장소가 같은 구간은 경로 API를 다시 부르지 않고 쓴다.
	 */
	void rebuild(TripDay day, LatLng stay, Map<Segment, LegRoute> routed) {
		calculate(day, stay, OpeningHours.byPlace(day.getItems().stream().map(TripItem::getPlace).toList(), openingHours),
				routed);
	}

	/**
	 * 일자의 구간 중 경로 API로 받은 값을 양 끝 장소로 모은다 (구간을 지우기 전에 부른다). 좌표 추정값은 모으지 않는다
	 * (다시 계산할 때 경로 API로 받을 수 있다).
	 */
	static Map<Segment, LegRoute> routedLegs(TripDay day) {
		return routedLegs(day.getLegs().stream()
				.filter(leg -> !leg.isEstimated())
				.map(leg -> new RoutedLeg(placeId(leg.getFromItem()), placeId(leg.getToItem()), leg.getMode(),
						leg.getDistanceM(), leg.getDurationMinutes(), leg.getFareKrw()))
				.toList());
	}

	/**
	 * 일자들의 구간 중 {@code routed}에 없는 것을 {@link LegRouter#routeAll}로 한 번에 받아 {@code routed}에 더한다.
	 * 항목이 seq 순서로 정렬돼 있어야 한다.
	 */
	void routeMissing(List<TripDay> days, LatLng stay, Map<Segment, LegRoute> routed) {
		Map<Segment, LegRouter.Endpoints> missing = new LinkedHashMap<>();
		for (TripDay day : days) {
			List<TripItem> items = day.getItems();
			if (items.isEmpty()) {
				continue;
			}
			TripItem previous = null;
			for (TripItem item : items) {
				addMissing(previous, item, stay, routed, missing);
				previous = item;
			}
			addMissing(previous, null, stay, routed, missing);
		}
		if (missing.isEmpty()) {
			return;
		}
		List<LegRoute> routes = legRouter.routeAll(List.copyOf(missing.values()));
		int i = 0;
		for (Segment segment : missing.keySet()) {
			routed.put(segment, routes.get(i++));
		}
	}

	private static void addMissing(TripItem from, TripItem to, LatLng stay, Map<Segment, LegRoute> routed,
			Map<Segment, LegRouter.Endpoints> missing) {
		Segment segment = new Segment(placeId(from), placeId(to));
		if (!routed.containsKey(segment)) {
			missing.putIfAbsent(segment, new LegRouter.Endpoints(locationOf(from, stay), locationOf(to, stay)));
		}
	}

	/** 경로 API로 받은 구간들을 양 끝 장소로 모은다. 동선을 다시 만들 때는 {@link TripRepository#findRoutedLegs}로 읽는다 */
	static Map<Segment, LegRoute> routedLegs(List<RoutedLeg> legs) {
		Map<Segment, LegRoute> routed = new HashMap<>();
		for (RoutedLeg leg : legs) {
			routed.putIfAbsent(new Segment(leg.fromPlaceId(), leg.toPlaceId()),
					new LegRoute(leg.mode(), leg.distanceM(), leg.durationMinutes(), leg.fareKrw(), false));
		}
		return routed;
	}

	/** 항목이 없으면 구간을 만들지 않고 숙소 출발·도착 시각을 비운다 */
	private void calculate(TripDay day, LatLng stay, Map<Long, OpeningHours> hoursByPlace,
			Map<Segment, LegRoute> routed) {
		List<TripItem> items = day.getItems();
		if (items.isEmpty()) {
			day.updateTimes(null, null);
			return;
		}
		routeMissing(List.of(day), stay, routed);
		LocalTime start = day.getDepartTime() != null ? day.getDepartTime() : properties.dayStart();
		int cursor = OpeningHours.minutes(start);
		TripItem previous = null;
		int legSeq = 0;
		for (TripItem item : items) {
			LegRoute route = routed.get(new Segment(placeId(previous), placeId(item)));
			day.addLeg(newLeg(day, legSeq++, previous, item, route));
			int arrive = waitForOpening(day.getTripDate(), cursor + route.durationMinutes(), item,
					hoursByPlace.getOrDefault(item.getPlace().getId(), OpeningHours.UNRESTRICTED));
			int depart = arrive + item.getStayMinutes();
			item.schedule(clock(arrive), clock(depart));
			cursor = depart;
			previous = item;
		}
		LegRoute back = routed.get(new Segment(placeId(previous), null));
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

	/** 항목이 null이면 숙소다 */
	private static LatLng locationOf(TripItem item, LatLng stay) {
		return item == null ? stay : LatLng.from(item.getPlace().getLocation());
	}

	/** 항목이 null이면 숙소(null)다 */
	private static Long placeId(TripItem item) {
		return item == null ? null : item.getPlace().getId();
	}

	private static Leg newLeg(TripDay day, int seq, TripItem from, TripItem to, LegRoute route) {
		return Leg.create(day, seq, from, to, route.mode(), route.distanceM(), route.durationMinutes(),
				route.fareKrw(), route.estimated());
	}

	/** 구간의 양 끝 장소 ID. null이면 숙소다 (숙소를 바꾸면 동선을 지우므로 한 여행의 숙소는 하나다) */
	record Segment(Long fromPlaceId, Long toPlaceId) {
	}
}
