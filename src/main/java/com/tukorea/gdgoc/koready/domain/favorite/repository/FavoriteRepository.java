package com.tukorea.gdgoc.koready.domain.favorite.repository;

import com.tukorea.gdgoc.koready.domain.favorite.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

	boolean existsByUserIdAndPlaceId(Long userId, Long placeId);

	/** 찜한다. 이미 찜했으면 아무것도 하지 않는다 (겹친 요청도 행은 하나) */
	@Modifying
	@Query(value = """
			INSERT INTO favorites (user_id, place_id, created_at) VALUES (:userId, :placeId, now())
			ON CONFLICT (user_id, place_id) DO NOTHING
			""", nativeQuery = true)
	void insertIfAbsent(Long userId, Long placeId);

	/** 찜을 푼다. 찜하지 않았으면 아무것도 하지 않는다 */
	@Modifying
	@Query("delete from Favorite f where f.user.id = :userId and f.place.id = :placeId")
	void deleteByUserIdAndPlaceId(Long userId, Long placeId);
}
