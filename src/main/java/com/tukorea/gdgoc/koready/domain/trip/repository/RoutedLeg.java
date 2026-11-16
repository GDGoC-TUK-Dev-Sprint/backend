package com.tukorea.gdgoc.koready.domain.trip.repository;

import com.tukorea.gdgoc.koready.domain.trip.entity.LegMode;

/**
 * 경로 API로 받은 이동 구간 하나의 양 끝 장소와 값 ({@link TripRepository#findRoutedLegs}).
 *
 * @param fromPlaceId 출발 장소. null이면 숙소
 * @param toPlaceId 도착 장소. null이면 숙소
 */
public record RoutedLeg(Long fromPlaceId, Long toPlaceId, LegMode mode, Integer distanceM, Short durationMinutes,
		Integer fareKrw) {
}
