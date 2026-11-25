package com.tukorea.gdgoc.koready.domain.trip.repository;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 여행에 담긴 장소 하나의 일자·도착 시각 ({@link TripItemRepository#findPlaceInTrip}).
 */
public record PlaceInTrip(Long tripId, Short dayNumber, LocalDate date, LocalTime arriveTime) {
}
