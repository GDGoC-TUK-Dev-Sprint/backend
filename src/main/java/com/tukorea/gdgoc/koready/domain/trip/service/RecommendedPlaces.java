package com.tukorea.gdgoc.koready.domain.trip.service;

import com.tukorea.gdgoc.koready.domain.trip.entity.Trip;
import java.util.List;

/**
 * 여행의 추천 장소 순위. 장소 후보 도메인이 구현한다 (추천 목록과 같은 순위).
 * 동선 만들기가 담은 장소가 적을 때 채우는 데 쓰고, trip이 candidate를 부르지 않게 trip 쪽에 둔다 (candidate → trip 한 방향).
 */
public interface RecommendedPlaces {

	/** 그 여행의 추천 목록(고른 카테고리, 추천순) 앞에서 {@code limit}곳의 장소 ID. 트랜잭션 안에서 부른다 */
	List<Long> topPlaceIds(Trip trip, int limit);
}
