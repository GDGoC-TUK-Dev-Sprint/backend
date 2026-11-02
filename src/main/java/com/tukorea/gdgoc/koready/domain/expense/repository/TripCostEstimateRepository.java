package com.tukorea.gdgoc.koready.domain.expense.repository;

import com.tukorea.gdgoc.koready.domain.expense.entity.TripCostEstimate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripCostEstimateRepository extends JpaRepository<TripCostEstimate, Long> {
}
