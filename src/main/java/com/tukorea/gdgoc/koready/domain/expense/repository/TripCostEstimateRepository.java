package com.tukorea.gdgoc.koready.domain.expense.repository;

import com.tukorea.gdgoc.koready.domain.expense.entity.TripCostEstimate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TripCostEstimateRepository extends JpaRepository<TripCostEstimate, Long> {

	/** 확정할 때 저장한 항목별 1인 예상 경비 */
	@Query("select e from TripCostEstimate e where e.trip.id = :tripId")
	List<TripCostEstimate> findByTripId(Long tripId);
}
