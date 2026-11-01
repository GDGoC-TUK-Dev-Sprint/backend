package com.tukorea.gdgoc.koready.domain.trip.dto;

import com.tukorea.gdgoc.koready.domain.meta.entity.Area;
import com.tukorea.gdgoc.koready.domain.meta.entity.Category;
import com.tukorea.gdgoc.koready.domain.meta.entity.Nationality;
import com.tukorea.gdgoc.koready.domain.trip.entity.StayType;
import com.tukorea.gdgoc.koready.global.dto.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;

/**
 * 여행 조건 요청 본문 (16 여행 만들기). 날짜·인원 범위, 희망 예산 통화, 숙소 종류별 필수 값은 {@code TripService}가 검사한다.
 */
public record TripRequest(
		@NotNull Nationality nationality,
		@NotNull LocalDate startDate,
		@NotNull LocalDate endDate,
		@NotNull Integer partySize,
		@Valid Money budgetPerPerson,
		Set<@NotNull Category> categories,
		@NotNull @Valid Stay stay) {

	/** 숙소 지정 방식. PLACE면 placeId, AREA면 areaCode가 필요하다 */
	public record Stay(@NotNull StayType type, Long placeId, Area areaCode) {
	}
}
