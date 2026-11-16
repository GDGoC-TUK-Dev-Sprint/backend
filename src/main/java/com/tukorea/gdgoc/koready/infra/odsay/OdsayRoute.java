package com.tukorea.gdgoc.koready.infra.odsay;

/**
 * ODsay 대중교통 경로 하나.
 *
 * @param durationMinutes 총 소요시간(분). 걷는 시간을 포함한다
 * @param distanceM 총 거리(m). 걷는 거리를 포함한다
 * @param fareKrw 1인 운임(원). 교통카드 성인 요금이다
 */
public record OdsayRoute(int durationMinutes, int distanceM, int fareKrw) {
}
