package com.tukorea.gdgoc.koready.domain.trip.repository;

import com.tukorea.gdgoc.koready.domain.trip.entity.TripDay;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TripDayRepository extends JpaRepository<TripDay, Long> {

	@Query("select d from TripDay d where d.trip.id = :tripId and d.dayNumber = :dayNumber")
	Optional<TripDay> findDay(Long tripId, Short dayNumber);

	/** 여러 여행의 일자 (여행 목록의 일자별 요약). 여행·일자 순서. 빈 목록으로 부르지 않는다 */
	@Query("select d from TripDay d where d.trip.id in :tripIds order by d.trip.id, d.dayNumber")
	List<TripDay> findDays(Collection<Long> tripIds);

	/** 순서 변경 때 같은 일자를 동시에 고치지 않게 행을 잠근다 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from TripDay d where d.trip.id = :tripId and d.dayNumber = :dayNumber")
	Optional<TripDay> findDayForUpdate(Long tripId, Short dayNumber);

	/** 장소 삭제 때 그 항목이 속한 일자의 행을 잠근다 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from TripDay d where d.id = :id")
	Optional<TripDay> findByIdForUpdate(Long id);
}
