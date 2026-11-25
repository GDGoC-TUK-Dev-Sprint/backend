package com.tukorea.gdgoc.koready.domain.trip.repository;

import com.tukorea.gdgoc.koready.domain.trip.entity.TripItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TripItemRepository extends JpaRepository<TripItem, Long> {

	/** 그 여행의 항목만 찾는다 (다른 여행의 항목이면 비어 있다) */
	@Query("select i from TripItem i join fetch i.tripDay d where i.id = :itemId and d.trip.id = :tripId")
	Optional<TripItem> findInTrip(Long tripId, Long itemId);

	/** 그 여행에 그 장소가 담긴 일정 항목 (일자·순서 순). 내 여행인지는 부르는 쪽({@code TripService})이 본다 */
	@Query("""
			select new com.tukorea.gdgoc.koready.domain.trip.repository.PlaceInTrip(
			    d.trip.id, d.dayNumber, d.tripDate, i.arriveTime)
			from TripItem i join i.tripDay d
			where d.trip.id = :tripId and i.place.id = :placeId
			order by d.dayNumber, i.seq
			""")
	List<PlaceInTrip> findPlaceInTrip(Long tripId, Long placeId);
}
