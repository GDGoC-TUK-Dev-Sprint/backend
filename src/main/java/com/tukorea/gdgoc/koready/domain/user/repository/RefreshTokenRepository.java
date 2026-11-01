package com.tukorea.gdgoc.koready.domain.user.repository;

import com.tukorea.gdgoc.koready.domain.user.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	@EntityGraph(attributePaths = "user")
	Optional<RefreshToken> findByTokenHash(String tokenHash);
}
