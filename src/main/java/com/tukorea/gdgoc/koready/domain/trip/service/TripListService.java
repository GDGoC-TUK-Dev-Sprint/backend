package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.meta.entity.Area;
import com.tukorea.gdgoc.koready.domain.place.dto.PlaceSummary;
import com.tukorea.gdgoc.koready.domain.place.entity.Place;
import com.tukorea.gdgoc.koready.domain.trip.config.TripProperties;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripListItem;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripPeriod;
import com.tukorea.gdgoc.koready.domain.trip.dto.TripResponse;
import com.tukorea.gdgoc.koready.domain.trip.entity.Trip;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripDay;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripItem;
import com.tukorea.gdgoc.koready.domain.trip.repository.TripDayRepository;
import com.tukorea.gdgoc.koready.domain.trip.repository.TripRepository;
import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.dto.PageQuery;
import com.tukorea.gdgoc.koready.global.dto.PageResponse;
import com.tukorea.gdgoc.koready.global.web.Language;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여행 목록: 메인의 여행 티켓, 지도·계획 확인 탭이 보여 줄 여행, 마이페이지 이전 여행 기록. 확정한 여행만 준다.
 */
@Service
@RequiredArgsConstructor
public class TripListService {

	/** 일자별 요약에 넣는 주요 장소 수 */
	private static final int MAX_PLACES = 3;

	private final TripRepository trips;
	private final TripDayRepository tripDays;
	private final TripService tripService;
	private final TripProperties properties;
	private final Clock clock;

	@Transactional(readOnly = true)
	public PageResponse<TripListItem> list(Long userId, TripPeriod period, PageQuery pageQuery, Language language,
			DisplayCurrency currency) {
		LocalDate today = LocalDate.now(clock);
		Page<Trip> page = period == TripPeriod.PAST
				? trips.findPast(userId, today, pageQuery.toPageable())
				: trips.findCurrent(userId, today, pageQuery.toPageable());
		List<Trip> found = page.getContent();
		List<Long> tripIds = found.stream().map(Trip::getId).toList();
		Set<Long> withExpenses = tripIds.isEmpty() ? Set.of() : trips.findTripIdsWithExpenses(tripIds);
		// 끝난 여행은 일자별 요약을 쓰는 화면이 없어 읽지 않는다
		Map<Long, List<TripDay>> daysByTrip = tripIds.isEmpty() || period == TripPeriod.PAST
				? Map.of()
				: tripDays.findDays(tripIds).stream().collect(Collectors.groupingBy(day -> day.getTrip().getId()));
		List<TripResponse> summaries = tripService.toResponses(found, language, currency);
		List<TripListItem> items = new ArrayList<>();
		for (int i = 0; i < found.size(); i++) {
			Long tripId = found.get(i).getId();
			items.add(new TripListItem(
					summaries.get(i),
					daysByTrip.getOrDefault(tripId, List.of()).stream().map(day -> daySummary(day, language)).toList(),
					withExpenses.contains(tripId)));
		}
		return PageResponse.of(items, pageQuery, page.getTotalElements());
	}

	/** 일자별 요약. 주요 장소와 대표 사진은 식사 자리를 빼고 고르고, 지역 이름은 그날 들르는 모든 곳에서 뽑는다 */
	private TripListItem.DaySummary daySummary(TripDay day, Language language) {
		List<Place> sights = day.getItems().stream()
				.filter(item -> item.getMealType() == null)
				.map(TripItem::getPlace)
				.toList();
		List<String> places = sights.stream()
				.limit(MAX_PLACES)
				.map(place -> language.pick(place.getNameKo(), place.getNameEn()))
				.toList();
		List<String> areaNames = day.getItems().stream()
				.map(item -> nearestArea(LatLng.from(item.getPlace().getLocation())))
				.flatMap(Optional::stream)
				.distinct()
				.map(area -> language.pick(area.getNameKo(), area.getNameEn()))
				.toList();
		PlaceSummary.Image image = sights.stream()
				.map(place -> PlaceSummary.Image.of(place.getImageUrl(), place.getThumbnailUrl(), place.getImageLicense()))
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
		return new TripListItem.DaySummary(day.getDayNumber(), day.getTripDate(), places, areaNames, image);
	}

	/**
	 * 장소에는 동네 이름이 없어, 지역(숙소 지역 목록) 중심 좌표에서 가장 가까운 지역을 그 장소의 지역으로 본다.
	 * 어느 지역 중심과도 먼 장소(설정값 {@code area-name-max-distance-m} 밖)는 가지 않는 동네 이름이 붙지 않게 지역이 없다
	 */
	private Optional<Area> nearestArea(LatLng location) {
		return Arrays.stream(Area.values())
				.min(Comparator.comparingDouble(area -> EstimatedLegRouter.distanceMeters(area.getCenter(), location)))
				.filter(area -> EstimatedLegRouter.distanceMeters(area.getCenter(), location)
						<= properties.areaNameMaxDistanceM());
	}
}
