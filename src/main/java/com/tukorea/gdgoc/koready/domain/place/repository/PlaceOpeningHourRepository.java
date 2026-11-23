package com.tukorea.gdgoc.koready.domain.place.repository;

import com.tukorea.gdgoc.koready.domain.place.entity.PlaceOpeningHour;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlaceOpeningHourRepository extends JpaRepository<PlaceOpeningHour, Long> {

	/** 여러 장소의 요일별 운영시간을 한 번에 읽는다 */
	@Query("select h from PlaceOpeningHour h where h.place.id in :placeIds")
	List<PlaceOpeningHour> findByPlaceIds(Collection<Long> placeIds);
}
