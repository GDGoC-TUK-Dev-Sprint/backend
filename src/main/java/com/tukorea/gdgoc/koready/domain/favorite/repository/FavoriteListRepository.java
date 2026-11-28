package com.tukorea.gdgoc.koready.domain.favorite.repository;

import com.tukorea.gdgoc.koready.domain.favorite.entity.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 찜 목록 조회. 카드에 쓰는 장소와 분류를 한 번에 읽는다.
 */
public interface FavoriteListRepository extends Repository<Favorite, Long> {

	/** 최근 찜한 순. 같은 시각이면 나중에 찜한(ID가 큰) 것이 먼저다 */
	@Query(value = """
			select f from Favorite f
			join fetch f.place p
			join fetch p.class1
			join fetch p.class3
			where f.user.id = :userId
			order by f.createdAt desc, f.id desc
			""",
			countQuery = "select count(f) from Favorite f where f.user.id = :userId")
	Page<Favorite> findRecent(Long userId, Pageable pageable);
}
