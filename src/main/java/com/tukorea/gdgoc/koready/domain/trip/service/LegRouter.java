package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.global.dto.LatLng;

/**
 * 두 지점 사이 이동시간·운임을 구한다.
 * TODO ODsay 연동 클라이언트({@code infra/odsay})가 생기면 그것을 부르는 구현을 만든다. ODsay를 부르지 못한 구간은
 * {@link EstimatedLegRouter}의 추정값으로 대신한다 ({@code estimated = true}).
 */
public interface LegRouter {

	LegRoute route(LatLng from, LatLng to);
}
