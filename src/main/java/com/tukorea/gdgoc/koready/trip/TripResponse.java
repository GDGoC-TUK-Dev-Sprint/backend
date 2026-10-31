package com.tukorea.gdgoc.koready.trip;

import com.tukorea.gdgoc.koready.common.Code;
import com.tukorea.gdgoc.koready.common.LatLng;
import com.tukorea.gdgoc.koready.common.Money;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 여행 요약 (Trip). 여행 만들기·여행 요약의 응답이고 다른 API 응답에도 들어간다. {@link TripService#toResponse}로 만든다.
 */
public record TripResponse(
		Long tripId,
		String ticketNo,
		TripStatus status,
		boolean editable,
		TripProgress progress,
		String title,
		Code nationality,
		LocalDate startDate,
		LocalDate endDate,
		int nights,
		int days,
		int partySize,
		Money budgetPerPerson,
		List<Code> categories,
		Stay stay,
		boolean routeCreated,
		OffsetDateTime confirmedAt) {

	/** 숙소. 이름은 장소면 장소 이름, 지역이면 지역 이름, 지도 핀이면 핀 주소다 */
	public record Stay(StayType type, Long placeId, String areaCode, String name, String nameKo, String address,
			LatLng location) {
	}
}
