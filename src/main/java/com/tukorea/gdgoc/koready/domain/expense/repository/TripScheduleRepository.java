package com.tukorea.gdgoc.koready.domain.expense.repository;

import com.tukorea.gdgoc.koready.domain.place.entity.FeeAudience;
import com.tukorea.gdgoc.koready.domain.trip.entity.MealType;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripDay;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 예상 경비 계산용 여행 일정 조회 (읽기 전용). 일정을 만들고 바꾸는 일은 trip 도메인이 한다.
 */
public interface TripScheduleRepository extends Repository<TripDay, Long> {

	@Query("select d from TripDay d where d.trip.id = :tripId order by d.dayNumber")
	List<TripDay> findDays(Long tripId);

	/** 여행의 모든 이동 구간 운임 (운임을 못 받은 구간은 null) */
	@Query("select l.tripDay.id as dayId, l.fareKrw as fareKrw from Leg l where l.tripDay.trip.id = :tripId")
	List<LegFare> findLegFares(Long tripId);

	/** 여행의 모든 일정 항목 (식사 자리면 mealType이 있다) */
	@Query("select i.tripDay.id as dayId, i.mealType as mealType, i.place.id as placeId "
			+ "from TripItem i where i.tripDay.trip.id = :tripId")
	List<ItemRef> findItems(Long tripId);

	@Query("select f.place.id as placeId, f.audience as audience, f.amountKrw as amountKrw "
			+ "from PlaceFee f where f.place.id in :placeIds")
	List<FeeRow> findFees(Collection<Long> placeIds);

	interface LegFare {
		Long getDayId();

		Integer getFareKrw();
	}

	interface ItemRef {
		Long getDayId();

		MealType getMealType();

		Long getPlaceId();
	}

	interface FeeRow {
		Long getPlaceId();

		FeeAudience getAudience();

		Integer getAmountKrw();
	}
}
