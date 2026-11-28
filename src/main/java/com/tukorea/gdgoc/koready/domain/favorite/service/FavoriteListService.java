package com.tukorea.gdgoc.koready.domain.favorite.service;

import com.tukorea.gdgoc.koready.domain.favorite.dto.FavoriteItem;
import com.tukorea.gdgoc.koready.domain.favorite.entity.Favorite;
import com.tukorea.gdgoc.koready.domain.favorite.repository.FavoriteListRepository;
import com.tukorea.gdgoc.koready.domain.place.dto.PlaceSummary;
import com.tukorea.gdgoc.koready.domain.place.entity.Place;
import com.tukorea.gdgoc.koready.global.dto.Code;
import com.tukorea.gdgoc.koready.global.dto.LatLng;
import com.tukorea.gdgoc.koready.global.dto.PageQuery;
import com.tukorea.gdgoc.koready.global.dto.PageResponse;
import com.tukorea.gdgoc.koready.global.util.SeoulTime;
import com.tukorea.gdgoc.koready.global.web.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 찜한 장소 목록: 마이페이지의 찜 개수·미리보기. 찜하기·찜 풀기는 {@link FavoriteService}다.
 */
@Service
@RequiredArgsConstructor
public class FavoriteListService {

	private final FavoriteListRepository favorites;

	/** 최근 찜한 순. {@code totalCount}가 찜 개수다. 사라진 장소(TourAPI에서 빠짐)·숙박도 찜한 그대로 준다 */
	@Transactional(readOnly = true)
	public PageResponse<FavoriteItem> list(Long userId, PageQuery pageQuery, Language language) {
		return PageResponse.from(favorites.findRecent(userId, pageQuery.toPageable()), favorite -> toItem(favorite, language));
	}

	private static FavoriteItem toItem(Favorite favorite, Language language) {
		return new FavoriteItem(placeSummary(favorite.getPlace(), language), SeoulTime.of(favorite.getCreatedAt()));
	}

	private static PlaceSummary placeSummary(Place place, Language language) {
		return new PlaceSummary(
				place.getId(),
				language.pick(place.getNameKo(), place.getNameEn()),
				place.getNameKo(),
				new Code(place.getClass1Code(), language.pick(place.getClass1().getNameKo(), place.getClass1().getNameEn())),
				new Code(place.getClass3Code(), language.pick(place.getClass3().getNameKo(), place.getClass3().getNameEn())),
				PlaceSummary.Image.of(place.getImageUrl(), place.getThumbnailUrl(), place.getImageLicense()),
				LatLng.from(place.getLocation()));
	}
}
