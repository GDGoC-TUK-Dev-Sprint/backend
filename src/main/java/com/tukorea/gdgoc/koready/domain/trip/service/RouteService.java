package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.candidate.config.CandidateProperties;
import com.tukorea.gdgoc.koready.domain.place.entity.Place;
import com.tukorea.gdgoc.koready.domain.place.repository.PlaceOpeningHourRepository;
import com.tukorea.gdgoc.koready.domain.place.repository.PlaceRepository;
import com.tukorea.gdgoc.koready.domain.trip.dto.RouteResponse;
import com.tukorea.gdgoc.koready.domain.trip.dto.UnscheduledReason;
import com.tukorea.gdgoc.koready.domain.trip.entity.Trip;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripDay;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripItemOrigin;
import com.tukorea.gdgoc.koready.domain.trip.repository.TripDayRepository;
import com.tukorea.gdgoc.koready.domain.trip.repository.TripRepository;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import com.tukorea.gdgoc.koready.global.exception.FieldErrors;
import com.tukorea.gdgoc.koready.global.web.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동선 만들기. 담은 장소를 검사하고 일자 배정·방문 순서를 계산해({@link RoutePlanner}) 일자·일정 항목·이동 구간을 저장한다.
 * 담은 장소가 권장 최소(일수 × 하루 권장 최소)보다 적으면 추천순으로 채운다 ({@code AUTO_FILL}).
 * 동선이 이미 있으면 지우고 새로 만든다 (일자별 일정에서 바꾼 순서도 초기화된다).
 */
@Service
@RequiredArgsConstructor
public class RouteService {

	private final TripService tripService;
	private final TripRepository trips;
	private final TripDayRepository days;
	private final PlaceRepository places;
	private final PlaceOpeningHourRepository openingHours;
	private final RecommendedPlaces recommendedPlaces;
	private final RoutePlanner planner;
	private final DayRecalculator recalculator;
	private final CandidateProperties candidateProperties;

	/**
	 * 동선을 만든다. 조건 다시 저장·확정과 겹치지 않게 사용자 행을 먼저 잠근다 (겹치면 바뀐 기간으로 지운 동선을 옛 기간으로
	 * 다시 만들거나 확정한 여행을 고칠 수 있다). 오류는 여행 없음(404) → 확정(409) → 중복(400) → 장소 수(400) → 후보 범위(400) 순서로 본다.
	 * 일자는 여행 일수만큼 만들고(장소가 없는 날 포함), 넣지 못한 담은 장소는 저장하지 않고 이유와 함께 돌려준다
	 * (자동으로 채운 장소는 못 넣어도 알리지 않는다).
	 */
	@Transactional
	public RouteResponse create(Long userId, Long tripId, List<Long> placeIds, Language language) {
		tripService.lockUser(userId);
		Trip trip = tripService.findMyTrip(userId, tripId);
		trip.requireEditable();
		validate(trip, placeIds);

		int fillCount = trip.getDays() * candidateProperties.recommendedMinPerDay() - placeIds.size();
		List<Long> fillCandidates = fillCandidates(trip, placeIds, fillCount);
		List<Long> allIds = new ArrayList<>(placeIds);
		allIds.addAll(fillCandidates);
		Map<Long, Place> placesById = places.findAllById(allIds).stream()
				.collect(Collectors.toMap(Place::getId, Function.identity()));
		Map<Long, OpeningHours> hoursByPlace = OpeningHours.byPlace(placesById.values(), openingHours);
		LatLng stay = LatLng.from(trip.getStayLocation());
		List<LocalDate> dates = trip.getStartDate().datesUntil(trip.getEndDate().plusDays(1)).toList();

		List<RoutePlanner.Stop> stops = new ArrayList<>();
		placeIds.forEach(id -> stops.add(stop(placesById.get(id), hoursByPlace, false)));
		// 여행 기간 내내 쉬는 추천 장소는 건너뛰고 다음 순위로 채운다
		fillCandidates.stream()
				.map(placesById::get)
				.filter(place -> dates.stream().anyMatch(hoursByPlace.get(place.getId())::opensOn))
				.limit(Math.max(fillCount, 0))
				.forEach(place -> stops.add(stop(place, hoursByPlace, true)));
		RoutePlanner.Plan plan = planner.plan(stay, dates, stops);

		trips.deleteDays(trip.getId());
		List<RouteResponse.Day> dayResponses = new ArrayList<>();
		for (int i = 0; i < dates.size(); i++) {
			TripDay day = TripDay.create(trip, i + 1, dates.get(i));
			for (RoutePlanner.Visit visit : plan.days().get(i)) {
				day.addItem(placesById.get(visit.stop().placeId()),
						visit.stop().filled() ? TripItemOrigin.AUTO_FILL : TripItemOrigin.SELECTED, null);
			}
			// 일정 항목 시각이 NOT NULL이라 시각을 채운 뒤 저장한다 (일자와 함께 항목·구간이 들어간다)
			recalculator.rebuild(day, stay, hoursByPlace);
			days.save(day);
			dayResponses.add(new RouteResponse.Day(day.getDayNumber(), day.getTripDate(), day.getItems().size()));
		}
		trip.changeTitle(titleKo(trip.getDays()), titleEn(trip.getDays()));

		List<RouteResponse.Unscheduled> unscheduled = new ArrayList<>();
		for (Map.Entry<Long, UnscheduledReason> entry : plan.unscheduled().entrySet()) {
			Place place = placesById.get(entry.getKey());
			unscheduled.add(new RouteResponse.Unscheduled(TripDayAssembler.placeSummary(place, language), entry.getValue()));
		}
		return new RouteResponse(tripService.toResponse(trip, language), dayResponses, unscheduled);
	}

	/**
	 * 같은 장소가 두 번이면 {@code 400 INVALID_REQUEST}, 일수 × 하루 최대 수를 넘으면 {@code 400 TOO_MANY_PLACES},
	 * 후보 범위(장소 후보와 같다) 밖이거나 없는 장소면 {@code 400 PLACE_NOT_SELECTABLE}과 그 장소 ID들({@code placeIds}, 요청 순서)이다.
	 * 조건을 다시 저장해 기간이 바뀌면 담아 둔 축제·행사가 기간 밖이 될 수 있어, 앱이 어느 장소를 뺄지 알게 한다.
	 */
	private void validate(Trip trip, List<Long> placeIds) {
		if (new HashSet<>(placeIds).size() != placeIds.size()) {
			new FieldErrors().add("placeIds", "같은 장소가 두 번 있습니다").throwIfAny();
		}
		if (placeIds.size() > trip.getDays() * candidateProperties.maxPerDay()) {
			throw new ApiException(ErrorCode.TOO_MANY_PLACES);
		}
		Set<Long> candidates = new HashSet<>(places.findCandidateIds(placeIds, trip.getStartDate(), trip.getEndDate()));
		List<Long> notSelectable = placeIds.stream().filter(id -> !candidates.contains(id)).toList();
		if (!notSelectable.isEmpty()) {
			throw new ApiException(ErrorCode.PLACE_NOT_SELECTABLE, Map.of("placeIds", notSelectable));
		}
	}

	/**
	 * 채울 후보: 추천 목록 앞에서 담은 장소를 뺀 장소 ID (추천순). 여행 기간 내내 쉬는 곳을 건너뛸 여유로 채울 수의 두 배를 읽는다.
	 * 채울 수가 없으면 빈 목록이다.
	 */
	private List<Long> fillCandidates(Trip trip, List<Long> placeIds, int fillCount) {
		if (fillCount <= 0) {
			return List.of();
		}
		Set<Long> chosen = new HashSet<>(placeIds);
		return recommendedPlaces.topPlaceIds(trip, placeIds.size() + fillCount * 2).stream()
				.filter(id -> !chosen.contains(id))
				.toList();
	}

	private static RoutePlanner.Stop stop(Place place, Map<Long, OpeningHours> hoursByPlace, boolean filled) {
		return new RoutePlanner.Stop(place.getId(), LatLng.from(place.getLocation()), place.getStayMinutes(),
				hoursByPlace.get(place.getId()), filled);
	}

	/** 여행 제목 템플릿 (LLM이 정해지기 전): "서울 2박 3일 여행", 당일이면 "서울 당일 여행" */
	static String titleKo(int days) {
		return days == 1 ? "서울 당일 여행" : "서울 %d박 %d일 여행".formatted(days - 1, days);
	}

	/** 영문 여행 제목 템플릿: "Seoul 3-Day Trip", 당일이면 "Seoul Day Trip" */
	static String titleEn(int days) {
		return days == 1 ? "Seoul Day Trip" : "Seoul %d-Day Trip".formatted(days);
	}
}
