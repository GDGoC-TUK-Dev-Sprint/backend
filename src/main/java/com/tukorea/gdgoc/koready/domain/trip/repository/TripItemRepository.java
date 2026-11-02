package com.tukorea.gdgoc.koready.domain.trip.repository;

import com.tukorea.gdgoc.koready.domain.trip.entity.TripItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TripItemRepository extends JpaRepository<TripItem, Long> {

	/** 그 여행의 항목만 찾는다 (다른 여행의 항목이면 비어 있다) */
	@Query("select i from TripItem i join fetch i.tripDay d where i.id = :itemId and d.trip.id = :tripId")
	Optional<TripItem> findInTrip(Long tripId, Long itemId);
}
