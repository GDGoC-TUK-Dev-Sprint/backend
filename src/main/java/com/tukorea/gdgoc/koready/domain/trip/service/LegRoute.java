package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.trip.entity.LegMode;

/**
 * 이동 구간 하나의 결과.
 *
 * @param fareKrw 1인 운임. 도보는 0, 운임을 모르면 null
 * @param estimated 좌표로 추정한 값이면 true
 */
public record LegRoute(LegMode mode, int distanceM, int durationMinutes, Integer fareKrw, boolean estimated) {
}
