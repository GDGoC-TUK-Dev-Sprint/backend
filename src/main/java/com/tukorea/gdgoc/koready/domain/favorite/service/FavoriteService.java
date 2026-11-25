package com.tukorea.gdgoc.koready.domain.favorite.service;

import com.tukorea.gdgoc.koready.domain.favorite.entity.Favorite;
import com.tukorea.gdgoc.koready.domain.favorite.repository.FavoriteRepository;
import com.tukorea.gdgoc.koready.domain.place.repository.PlaceRepository;
import com.tukorea.gdgoc.koready.domain.user.repository.UserRepository;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 찜하기·찜 풀기. 둘 다 이미 그 상태여도 성공이다 (같은 요청을 다시 보내도 결과가 같다).
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

	private final FavoriteRepository favorites;
	private final UserRepository users;
	private final PlaceRepository places;

	/**
	 * 찜한다. 사라진 장소(TourAPI에서 빠짐)·숙박도 찜할 수 있다.
	 * 트랜잭션으로 묶지 않는다: 넣다가 제약에 걸리면(겹친 요청이 먼저 찜함, 그 사이 탈퇴·장소 삭제) 다시 확인해 답을 정하는데,
	 * 한 트랜잭션 안이면 제약 위반 뒤의 조회가 실패한다.
	 *
	 * @throws ApiException 탈퇴한 사용자면 {@code 401 UNAUTHORIZED}, 없는 장소면 {@code 404 PLACE_NOT_FOUND}
	 */
	public void add(Long userId, Long placeId) {
		requireUserAndPlace(userId, placeId);
		if (favorites.existsByUserIdAndPlaceId(userId, placeId)) {
			return;
		}
		try {
			favorites.saveAndFlush(Favorite.create(users.getReferenceById(userId), places.getReferenceById(placeId)));
		} catch (DataIntegrityViolationException e) {
			requireUserAndPlace(userId, placeId);
			if (!favorites.existsByUserIdAndPlaceId(userId, placeId)) {
				throw e;
			}
		}
	}

	private void requireUserAndPlace(Long userId, Long placeId) {
		if (!users.existsById(userId)) {
			throw new ApiException(ErrorCode.UNAUTHORIZED);
		}
		if (!places.existsById(placeId)) {
			throw new ApiException(ErrorCode.PLACE_NOT_FOUND);
		}
	}

	/** 찜을 푼다. 찜하지 않았거나 없는 장소여도 성공이다 */
	@Transactional
	public void remove(Long userId, Long placeId) {
		favorites.deleteByUserIdAndPlaceId(userId, placeId);
	}
}
