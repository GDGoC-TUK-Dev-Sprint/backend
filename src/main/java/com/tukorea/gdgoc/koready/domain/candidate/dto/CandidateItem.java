package com.tukorea.gdgoc.koready.domain.candidate.dto;

import com.tukorea.gdgoc.koready.domain.place.dto.PlaceSummary;
import com.tukorea.gdgoc.koready.domain.place.entity.SignalType;
import java.util.List;

/**
 * 장소 후보 한 곳.
 *
 * @param rank 추천순 순위 (1부터, 필터를 걸면 그 안에서). 검색이면 null
 * @param distanceFromStayM 숙소에서 직선거리 (m)
 * @param signals 순위에 쓴 인기도 신호
 * @param inTrip 이 여행에 이미 담은 장소 (장소 선택으로 돌아왔을 때 "담김")
 */
public record CandidateItem(Long rank, PlaceSummary place, long distanceFromStayM, List<CandidateReason> reasons,
		List<SignalType> signals, boolean inTrip) {
}
