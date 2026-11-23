package com.tukorea.gdgoc.koready.domain.user.repository;

import com.tukorea.gdgoc.koready.domain.user.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	@EntityGraph(attributePaths = "user")
	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/** 조회 없이 바로 지운다. 지운 행 수(0 또는 1)를 돌려준다. */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from RefreshToken r where r.tokenHash = :tokenHash")
	int deleteByTokenHash(@Param("tokenHash") String tokenHash);

	/** 그 사용자의 만료된 리프레시 토큰을 지운다 (로그인할 때, API 명세 2). 지운 행 수를 돌려준다 */
	@Modifying(flushAutomatically = true)
	@Query("delete from RefreshToken r where r.user.id = :userId and r.expiresAt <= :now")
	int deleteExpiredByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
