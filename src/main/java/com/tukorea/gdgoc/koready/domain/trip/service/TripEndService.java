package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.trip.entity.Trip;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripProgress;
import com.tukorea.gdgoc.koready.domain.trip.entity.TripStatus;
import com.tukorea.gdgoc.koready.domain.trip.repository.TripEndRepository;
import com.tukorea.gdgoc.koready.domain.trip.repository.TripRepository;
import com.tukorea.gdgoc.koready.domain.user.entity.User;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여행 종료 (API 명세 33, 마이페이지 "여행 종료하기"). 진행 중인 확정 여행의 종료일을 어제로 당겨 끝난 여행으로 만든다.
 * 그러면 기존 규칙이 그대로 적용된다: 여행 목록의 {@code PAST}에 나오고, 실제 지출 입력·리포트를 받고, 새 여행을 만들 수 있다.
 * 종료일은 실제로 여행한 마지막 날(어제)이 된다.
 */
@Service
@RequiredArgsConstructor
public class TripEndService {

	private static final String NOT_ONGOING = "진행 중인 확정 여행이 아닙니다";
	private static final String STARTS_TODAY = "여행 시작일에는 종료할 수 없습니다. 다음 날부터 종료할 수 있습니다";

	private final TripRepository trips;
	private final TripEndRepository tripEnds;
	private final EntityManager entityManager;
	private final Clock clock;

	/**
	 * 내 진행 중인 확정 여행을 끝낸다. 이미 끝난 여행이면 아무것도 하지 않는다.
	 * 여행 만들기·확정과 겹치지 않게 사용자 행을 잠근다 (그 서비스들과 같은 방식).
	 *
	 * @throws ApiException 탈퇴한 사용자면 {@code 401 UNAUTHORIZED}, 내 여행이 아니면 {@code 404 TRIP_NOT_FOUND},
	 *         확정 전이거나 아직 시작하지 않았거나 시작일 당일이면 {@code 400 INVALID_REQUEST}
	 */
	@Transactional
	public void end(Long userId, Long tripId) {
		if (entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE) == null) {
			throw new ApiException(ErrorCode.UNAUTHORIZED);
		}
		// 남의 여행이면 있는지 알리지 않고 없는 여행과 같게 답한다
		Trip trip = trips.findById(tripId)
				.filter(found -> found.getUser().getId().equals(userId))
				.orElseThrow(() -> new ApiException(ErrorCode.TRIP_NOT_FOUND));
		LocalDate today = LocalDate.now(clock);
		if (trip.getStatus() != TripStatus.CONFIRMED) {
			throw new ApiException(ErrorCode.INVALID_REQUEST, NOT_ONGOING);
		}
		TripProgress progress = trip.progressOn(today);
		if (progress == TripProgress.COMPLETED) {
			return;
		}
		if (progress == TripProgress.UPCOMING) {
			throw new ApiException(ErrorCode.INVALID_REQUEST, NOT_ONGOING);
		}
		// 종료일은 시작일보다 앞설 수 없어서(ck_trips_end_date) 시작일 당일에는 어제로 당길 수 없다
		if (!trip.getStartDate().isBefore(today)) {
			throw new ApiException(ErrorCode.INVALID_REQUEST, STARTS_TODAY);
		}
		tripEnds.updateEndDate(trip.getId(), today.minusDays(1));
	}
}
