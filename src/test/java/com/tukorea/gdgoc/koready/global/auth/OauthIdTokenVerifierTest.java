package com.tukorea.gdgoc.koready.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tukorea.gdgoc.koready.domain.user.entity.OauthProvider;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class OauthIdTokenVerifierTest {

	private final OauthIdTokenVerifier verifier = new OauthIdTokenVerifier(List.of("google-client"), "apple-client");

	/** JWT 형식이 아니면 제공자 공개키를 받기 전에 걸러진다 (네트워크를 쓰지 않는다) */
	@Test
	void malformedTokenIsInvalidIdToken() {
		for (OauthProvider provider : OauthProvider.values()) {
			assertThatThrownBy(() -> verifier.verify(provider, "not-a-jwt"))
					.isInstanceOfSatisfying(ApiException.class,
							e -> assertThat(e.getErrorCode())
									.isEqualTo(ErrorCode.INVALID_ID_TOKEN));
		}
	}
}
