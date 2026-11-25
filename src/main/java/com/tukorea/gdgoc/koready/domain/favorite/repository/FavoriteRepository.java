package com.tukorea.gdgoc.koready.domain.favorite.repository;

import com.tukorea.gdgoc.koready.domain.favorite.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

	boolean existsByUserIdAndPlaceId(Long userId, Long placeId);

	/** 찜을 푼다. 찜하지 않았으면 아무것도 하지 않는다 */
	@Modifying
	@Query("delete from Favorite f where f.user.id = :userId and f.place.id = :placeId")
	void deleteByUserIdAndPlaceId(Long userId, Long placeId);
}
