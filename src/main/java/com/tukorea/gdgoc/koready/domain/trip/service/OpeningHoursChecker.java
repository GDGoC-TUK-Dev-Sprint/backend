package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.place.entity.HoursType;
import com.tukorea.gdgoc.koready.domain.place.entity.Place;
import com.tukorea.gdgoc.koready.domain.place.entity.PlaceOpeningHour;
import com.tukorea.gdgoc.koready.domain.place.repository.PlaceOpeningHourRepository;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripDay;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripItem;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 일자별 일정의 {@code OUTSIDE_OPENING_HOURS} 경고를 정한다. 저장하지 않고 조회할 때마다 계산한다.
 * 운영시간이 {@code STRUCTURED}가 아닌 장소(상시 개방·확인 필요)는 제약이 없다고 본다.
 */
@Component
@RequiredArgsConstructor
class OpeningHoursChecker {

	private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd");
	private static final int MINUTES_PER_DAY = 24 * 60;

	private final PlaceOpeningHourRepository openingHours;

	/** 운영시간 밖에 머무는 항목의 ID */
	Set<Long> findItemsOutsideHours(TripDay day) {
		Set<Long> structuredPlaceIds = day.getItems().stream()
				.map(TripItem::getPlace)
				.filter(place -> place.getHoursType() == HoursType.STRUCTURED)
				.map(Place::getId)
				.collect(Collectors.toSet());
		if (structuredPlaceIds.isEmpty()) {
			return Set.of();
		}
		Map<Long, List<PlaceOpeningHour>> hoursByPlace = openingHours.findByPlaceIds(structuredPlaceIds).stream()
				.collect(Collectors.groupingBy(hour -> hour.getPlace().getId()));

		Set<Long> outside = new HashSet<>();
		for (TripItem item : day.getItems()) {
			Long placeId = item.getPlace().getId();
			if (structuredPlaceIds.contains(placeId)
					&& !isOpen(item, day.getTripDate(), hoursByPlace.getOrDefault(placeId, List.of()))) {
				outside.add(item.getId());
			}
		}
		return outside;
	}

	/** 방문 요일의 운영시간 안에 머무는지. 그 날짜에 드는 계절 행이 있으면 그것을, 없으면 연중 행을 쓴다. 행이 없는 요일은 정기 휴무다 */
	private static boolean isOpen(TripItem item, LocalDate date, List<PlaceOpeningHour> hours) {
		int dayOfWeek = date.getDayOfWeek().getValue();
		List<PlaceOpeningHour> sameDay = hours.stream()
				.filter(hour -> hour.getDayOfWeek() == dayOfWeek)
				.toList();
		List<PlaceOpeningHour> seasonal = sameDay.stream()
				.filter(hour -> isSeasonal(hour) && coversSeason(hour, date))
				.toList();
		List<PlaceOpeningHour> applicable = seasonal.isEmpty()
				? sameDay.stream().filter(hour -> !isSeasonal(hour)).toList()
				: seasonal;
		return applicable.stream().anyMatch(hour -> covers(hour, item.getArriveTime(), item.getDepartTime()));
	}

	private static boolean isSeasonal(PlaceOpeningHour hour) {
		return hour.getSeasonFrom() != null && hour.getSeasonTo() != null;
	}

	/** 날짜가 계절 구간에 드는지. 11-01~02-28처럼 해를 넘기는 구간도 다룬다 */
	private static boolean coversSeason(PlaceOpeningHour hour, LocalDate date) {
		String monthDay = date.format(MONTH_DAY);
		String from = hour.getSeasonFrom();
		String to = hour.getSeasonTo();
		if (from.compareTo(to) <= 0) {
			return monthDay.compareTo(from) >= 0 && monthDay.compareTo(to) <= 0;
		}
		return monthDay.compareTo(from) >= 0 || monthDay.compareTo(to) <= 0;
	}

	/** arrive~depart 동안 머물 수 있는지. 자정을 넘겨 영업하는 곳과 입장 마감 시각을 다룬다. 휴게시간은 아직 보지 않는다 */
	private static boolean covers(PlaceOpeningHour hour, LocalTime arrive, LocalTime depart) {
		int open = minutes(hour.getOpenTime());
		int close = minutes(hour.getCloseTime());
		if (close <= open) {
			close += MINUTES_PER_DAY;
		}
		int from = minutes(arrive);
		int to = minutes(depart);
		if (to < from) {
			to += MINUTES_PER_DAY;
		}
		if (from < open && from + MINUTES_PER_DAY <= close) {
			// 자정 넘어 영업하는 곳에 자정 뒤에 도착
			from += MINUTES_PER_DAY;
			to += MINUTES_PER_DAY;
		}
		if (from < open || to > close) {
			return false;
		}
		if (hour.getLastEntryTime() == null) {
			return true;
		}
		int lastEntry = minutes(hour.getLastEntryTime());
		if (lastEntry < open) {
			lastEntry += MINUTES_PER_DAY;
		}
		return from <= lastEntry;
	}

	private static int minutes(LocalTime time) {
		return time.getHour() * 60 + time.getMinute();
	}
}
