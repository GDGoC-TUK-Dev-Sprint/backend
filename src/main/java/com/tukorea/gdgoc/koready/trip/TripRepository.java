package com.tukorea.gdgoc.koready.trip;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TripRepository extends JpaRepository<Trip, Long> {

	/** 끝나지 않은(종료일 ≥ 오늘) 확정 여행. 사용자마다 0~1개다 */
	@Query("select t.id from Trip t where t.user.id = :userId and t.status = CONFIRMED and t.endDate >= :today")
	List<Long> findActiveTripIds(Long userId, LocalDate today);

	/** 확정 전 여행을 지운다. 딸린 일자·일정 항목·이동 구간·고른 카테고리는 DB가 함께 지운다 (ON DELETE CASCADE) */
	@Modifying
	@Query("delete from Trip t where t.user.id = :userId and t.status = DRAFT")
	void deleteDrafts(Long userId);

	/** 동선을 만들었는지 (일자가 있는지) */
	@Query("select count(d) > 0 from TripDay d where d.trip.id = :tripId")
	boolean hasDays(Long tripId);
}
