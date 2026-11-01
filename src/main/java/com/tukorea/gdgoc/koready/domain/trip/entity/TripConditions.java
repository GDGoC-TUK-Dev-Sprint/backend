package com.tukorea.gdgoc.koready.domain.trip.entity;

import com.tukorea.gdgoc.koready.domain.meta.entity.Area;
import com.tukorea.gdgoc.koready.domain.meta.entity.Category;
import com.tukorea.gdgoc.koready.domain.meta.entity.Nationality;
import com.tukorea.gdgoc.koready.domain.place.entity.Place;
import java.time.LocalDate;
import java.util.Set;
import org.locationtech.jts.geom.Point;

/**
 * 검사를 마친 여행 조건. 희망 예산은 원화다.
 */
public record TripConditions(
		Nationality nationality,
		LocalDate startDate,
		LocalDate endDate,
		int partySize,
		Integer budgetPerPersonKrw,
		Set<Category> categories,
		Stay stay) {

	/** 숙소. 이름·주소·좌표는 고른 시점의 값을 그대로 저장한다 */
	public record Stay(StayType type, Place place, Area area, String name, String address, Point location) {

		/** 검색해서 고른 숙박 장소 */
		public static Stay of(Place lodging) {
			return new Stay(StayType.PLACE, lodging, null, lodging.getNameKo(), lodging.getAddress(),
					lodging.getLocation());
		}

		/** 숙소를 정하지 못해 고른 지역. 지역 중심을 숙소로 본다 */
		public static Stay of(Area area) {
			return new Stay(StayType.AREA, null, area, area.getNameKo(), null, area.getCenter().toPoint());
		}
	}
}
