package com.tukorea.gdgoc.koready.domain.trip.repository;

import com.tukorea.gdgoc.koready.domain.trip.entity.Trip;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 여행 종료용 갱신. 엔티티에 setter가 없어서 종료일만 바로 고친다.
 */
public interface TripEndRepository extends Repository<Trip, Long> {

	/** 종료일을 바꾼다. 읽어 둔 여행 엔티티는 영속성 컨텍스트에 남으므로 부르는 쪽이 이 뒤에 그 엔티티를 쓰지 않는다 */
	@Modifying(clearAutomatically = true)
	@Query("update Trip t set t.endDate = :endDate where t.id = :tripId")
	void updateEndDate(Long tripId, LocalDate endDate);
}
