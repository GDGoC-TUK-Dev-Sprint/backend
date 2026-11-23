package com.tukorea.gdgoc.koready.domain.trip.repository;

import com.tukorea.gdgoc.koready.domain.trip.entity.Trip;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TripRepository extends JpaRepository<Trip, Long> {

	/** 끝나지 않은(종료일 ≥ 오늘) 확정 여행. 사용자마다 0~1개다 */
	@Query("select t.id from Trip t where t.user.id = :userId and t.status = CONFIRMED and t.endDate >= :today")
	List<Long> findActiveTripIds(Long userId, LocalDate today);

	/** 끝나지 않은(종료일 ≥ 오늘) 확정 여행 목록 */
	@Query("""
			select t from Trip t
			where t.user.id = :userId and t.status = CONFIRMED and t.endDate >= :today
			order by t.startDate, t.id
			""")
	Page<Trip> findCurrent(Long userId, LocalDate today, Pageable pageable);

	/** 끝난(종료일이 오늘보다 앞인) 확정 여행 목록. 종료일 최근 순 */
	@Query("""
			select t from Trip t
			where t.user.id = :userId and t.status = CONFIRMED and t.endDate < :today
			order by t.endDate desc, t.id desc
			""")
	Page<Trip> findPast(Long userId, LocalDate today, Pageable pageable);

	/**
	 * 실제 지출을 저장한 여행. 지출은 expense 도메인 것이고 expense가 trip을 쓰므로, 엔티티를 가져오지 않고 테이블만 본다.
	 * 빈 목록으로 부르지 않는다
	 */
	@Query(value = "select distinct trip_id from trip_expenses where trip_id in (:tripIds)", nativeQuery = true)
	Set<Long> findTripIdsWithExpenses(Collection<Long> tripIds);

	/** 확정 전 여행을 지운다. 딸린 일자·일정 항목·이동 구간·고른 카테고리는 DB가 함께 지운다 (ON DELETE CASCADE) */
	@Modifying
	@Query("delete from Trip t where t.user.id = :userId and t.status = DRAFT")
	void deleteDrafts(Long userId);

	/** 동선을 만들었는지 (일자가 있는지) */
	@Query("select count(d) > 0 from TripDay d where d.trip.id = :tripId")
	boolean hasDays(Long tripId);

	/** 동선을 만든(일자가 있는) 여행. 여러 여행을 한 번에 본다. 빈 목록으로 부르지 않는다 */
	@Query("select distinct d.trip.id from TripDay d where d.trip.id in :tripIds")
	Set<Long> findTripIdsWithDays(Collection<Long> tripIds);

	/**
	 * 여행의 이동 구간 중 경로 API로 받은 것. 동선을 다시 만들 때 같은 장소 쌍은 경로 API를 다시 부르지 않는다.
	 * 엔티티를 읽지 않으므로 {@link #deleteDays} 전에 불러도 된다
	 */
	@Query("""
			select new com.tukorea.gdgoc.koready.domain.trip.repository.RoutedLeg(
			    fromPlace.id, toPlace.id, l.mode, l.distanceM, l.durationMinutes, l.fareKrw)
			from Leg l
			left join l.fromItem fromItem left join fromItem.place fromPlace
			left join l.toItem toItem left join toItem.place toPlace
			where l.tripDay.trip.id = :tripId and l.estimated = false
			""")
	List<RoutedLeg> findRoutedLegs(Long tripId);

	/**
	 * 동선(일자)을 지운다. 딸린 일정 항목·이동 구간은 DB가 함께 지운다 (ON DELETE CASCADE).
	 * SQL로 바로 지우므로, 이미 읽어 둔 일자·일정 항목 엔티티는 영속성 컨텍스트에 남는다 (읽기 전에 부른다)
	 */
	@Modifying
	@Query("delete from TripDay d where d.trip.id = :tripId")
	void deleteDays(Long tripId);
}
