package com.tukorea.gdgoc.koready.domain.trip.dto;

import com.tukorea.gdgoc.koready.domain.place.dto.PlaceSummary;
import java.time.LocalDate;
import java.util.List;

/**
 * 동선 만들기 응답. 일자별 일정은 일자별 일정 조회로 따로 받는다.
 *
 * @param trip 제목이 들어간 여행 요약
 * @param days 일자 탭 (여행 일수만큼, 장소가 없는 날도 있다)
 * @param unscheduled 동선에 넣지 못한 장소 (요청 순서, 저장하지 않는다)
 */
public record RouteResponse(TripResponse trip, List<Day> days, List<Unscheduled> unscheduled) {

	/** @param itemCount 그날 일정 항목 수 (식사 자리 포함) */
	public record Day(int dayNumber, LocalDate date, int itemCount) {
	}

	public record Unscheduled(PlaceSummary place, UnscheduledReason reason) {
	}
}
