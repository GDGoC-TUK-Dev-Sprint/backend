package com.tukorea.gdgoc.koready.domain.user.repository;

import com.tukorea.gdgoc.koready.domain.user.entity.OauthAccount;
import com.tukorea.gdgoc.koready.domain.user.entity.OauthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OauthAccountRepository extends JpaRepository<OauthAccount, Long> {

	@EntityGraph(attributePaths = "user")
	Optional<OauthAccount> findByProviderAndSubject(OauthProvider provider, String subject);
}
