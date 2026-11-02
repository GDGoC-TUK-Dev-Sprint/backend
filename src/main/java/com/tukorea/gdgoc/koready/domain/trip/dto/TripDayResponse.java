package com.tukorea.gdgoc.koready.domain.trip.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tukorea.gdgoc.koready.domain.place.dto.PlaceSummary;
import com.tukorea.gdgoc.koready.domain.trip.entity.LegMode;
import com.tukorea.gdgoc.koready.domain.trip.entity.MealType;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripItemOrigin;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.dto.Money;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 일자별 일정 (TripDay). 일자별 일정 조회의 응답이고, 순서 변경·장소 삭제도 그 일자를 이 모양으로 돌려준다.
 * 금액은 원화다 (환율 환산 전).
 */
public record TripDayResponse(
		int dayNumber,
		LocalDate date,
		@JsonFormat(pattern = "HH:mm") LocalTime departTime,
		@JsonFormat(pattern = "HH:mm") LocalTime returnTime,
		Summary summary,
		Stay stay,
		List<Item> items,
		List<LegResponse> legs) {

	/**
	 * 일자 요약.
	 *
	 * @param totalStayMinutes 장소에 머무는 시간의 합
	 * @param totalTravelMinutes 숙소 왕복을 포함한 이동시간의 합
	 * @param farePerPerson 대중교통 1인 운임의 합. 운임을 모르는 구간이 하나라도 있으면 null
	 */
	public record Summary(int itemCount, int totalStayMinutes, int totalTravelMinutes, Money farePerPerson) {
	}

	/** 숙소 (출발·도착 카드, 지도의 숙소 마커) */
	public record Stay(String name, LatLng location) {
	}

	public record Item(
			Long itemId,
			int seq,
			PlaceSummary place,
			TripItemOrigin origin,
			MealType mealType,
			@JsonFormat(pattern = "HH:mm") LocalTime arriveTime,
			@JsonFormat(pattern = "HH:mm") LocalTime departTime,
			int stayMinutes,
			List<Warning> warnings) {
	}

	public enum Warning {
		/** 순서를 바꿔 운영시간 밖에 머문다 */
		OUTSIDE_OPENING_HOURS
	}

	/** 이동 구간. fromItemId·toItemId가 null이면 숙소다. from·to는 지도 동선 선의 양 끝이다 */
	public record LegResponse(
			int seq,
			Long fromItemId,
			Long toItemId,
			LatLng from,
			LatLng to,
			LegMode mode,
			int durationMinutes,
			int distanceM,
			Money fare,
			boolean estimated) {
	}
}
