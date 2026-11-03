package com.tukorea.gdgoc.koready.domain.place.repository;

import com.tukorea.gdgoc.koready.domain.place.entity.Place;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlaceRepository extends JpaRepository<Place, Long> {

	/** 주어진 장소 중 그 여행 기간의 후보 범위({@link PlaceSql#CANDIDATE_SCOPE})에 드는 장소의 ID. 없는 ID는 빠진다 */
	@Query(value = "SELECT p.id FROM places p WHERE p.id IN (:placeIds) AND " + PlaceSql.CANDIDATE_SCOPE,
			nativeQuery = true)
	List<Long> findCandidateIds(Collection<Long> placeIds, LocalDate startDate, LocalDate endDate);
}
