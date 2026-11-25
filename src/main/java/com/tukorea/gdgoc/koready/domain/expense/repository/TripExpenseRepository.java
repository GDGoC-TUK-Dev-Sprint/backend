package com.tukorea.gdgoc.koready.domain.expense.repository;

import com.tukorea.gdgoc.koready.domain.expense.entity.TripExpense;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TripExpenseRepository extends JpaRepository<TripExpense, Long> {

	/** 여행의 실제 지출 (보낸 순서) */
	@Query("select e from TripExpense e where e.trip.id = :tripId order by e.seq")
	List<TripExpense> findByTripId(Long tripId);

	/** 여행의 실제 지출을 모두 지운다 (저장은 통째로 덮어쓴다) */
	@Modifying(flushAutomatically = true)
	@Query("delete from TripExpense e where e.trip.id = :tripId")
	int deleteByTripId(Long tripId);
}
