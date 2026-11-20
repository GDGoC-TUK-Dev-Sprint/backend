package com.tukorea.gdgoc.koready.domain.user.repository;

import com.tukorea.gdgoc.koready.domain.user.entity.RefreshToken;
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
}
