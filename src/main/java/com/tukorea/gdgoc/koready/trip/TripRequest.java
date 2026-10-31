package com.tukorea.gdgoc.koready.trip;

import com.tukorea.gdgoc.koready.common.Money;
import com.tukorea.gdgoc.koready.meta.Area;
import com.tukorea.gdgoc.koready.meta.Category;
import com.tukorea.gdgoc.koready.meta.Nationality;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;

/**
 * 여행 조건 요청 본문 (16 여행 만들기). 날짜·인원 범위, 희망 예산 통화, 숙소 종류별 필수 값은 {@link TripService}가 검사한다.
 */
record TripRequest(
		@NotNull Nationality nationality,
		@NotNull LocalDate startDate,
		@NotNull LocalDate endDate,
		@NotNull Integer partySize,
		@Valid Money budgetPerPerson,
		Set<@NotNull Category> categories,
		@NotNull @Valid Stay stay) {

	/** 숙소 지정 방식. PLACE면 placeId, AREA면 areaCode가 필요하다 */
	record Stay(@NotNull StayType type, Long placeId, Area areaCode) {
	}
}
