package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.place.repository.PlaceOpeningHourRepository;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripDay;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripItem;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 일자별 일정의 {@code OUTSIDE_OPENING_HOURS} 경고를 정한다. 저장하지 않고 조회할 때마다 계산한다.
 * 판정 규칙은 동선 만들기와 같은 {@link OpeningHours}다 (운영시간이 {@code STRUCTURED}가 아닌 장소는 제약이 없다).
 */
@Component
@RequiredArgsConstructor
class OpeningHoursChecker {

	private final PlaceOpeningHourRepository openingHours;

	/** 운영시간 밖에 머무는 항목의 ID */
	Set<Long> findItemsOutsideHours(TripDay day) {
		Map<Long, OpeningHours> hoursByPlace = OpeningHours.byPlace(
				day.getItems().stream().map(TripItem::getPlace).toList(), openingHours);
		Set<Long> outside = new HashSet<>();
		for (TripItem item : day.getItems()) {
			OpeningHours hours = hoursByPlace.get(item.getPlace().getId());
			if (!hours.canStay(day.getTripDate(), item.getArriveTime(), item.getDepartTime())) {
				outside.add(item.getId());
			}
		}
		return outside;
	}
}
