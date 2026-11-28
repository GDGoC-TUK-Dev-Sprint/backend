package com.tukorea.gdgoc.koready.domain.favorite.dto;

import com.tukorea.gdgoc.koready.domain.place.dto.PlaceSummary;
import java.time.OffsetDateTime;

/**
 * 찜한 장소 한 건 (API 명세 13, 마이페이지-02).
 *
 * @param favoritedAt 찜한 시각 (서울 시각 +09:00)
 */
public record FavoriteItem(PlaceSummary place, OffsetDateTime favoritedAt) {
}
