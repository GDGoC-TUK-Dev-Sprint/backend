package com.tukorea.gdgoc.koready.domain.user.repository;

import com.tukorea.gdgoc.koready.domain.user.entity.OauthAccount;
import com.tukorea.gdgoc.koready.domain.user.entity.OauthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OauthAccountRepository extends JpaRepository<OauthAccount, Long> {

	@EntityGraph(attributePaths = "user")
	Optional<OauthAccount> findByProviderAndSubject(OauthProvider provider, String subject);

	/** 사용자의 소셜 계정 (재발급 응답의 로그인 수단용). 여럿이면 먼저 연결한 것 */
	Optional<OauthAccount> findFirstByUserIdOrderByIdAsc(Long userId);
}
