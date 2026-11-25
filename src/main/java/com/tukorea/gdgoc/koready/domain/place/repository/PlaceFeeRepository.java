package com.tukorea.gdgoc.koready.domain.place.repository;

import com.tukorea.gdgoc.koready.domain.place.entity.PlaceFee;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceFeeRepository extends JpaRepository<PlaceFee, Long> {

	/** 장소 하나의 대상별 요금 (적재한 순서) */
	List<PlaceFee> findByPlaceIdOrderById(Long placeId);
}
