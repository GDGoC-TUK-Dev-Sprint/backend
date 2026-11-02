package com.tukorea.gdgoc.koready.global.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.tukorea.gdgoc.koready.domain.user.entity.OauthProvider;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 앱이 보낸 Google/Apple ID 토큰을 검증한다: 서명(제공자 공개키), 만료, 발급자(iss), 대상(aud).
 */
@Component
public class OauthIdTokenVerifier {

	public record Identity(String subject, String name) {}

	private record Rule(DefaultJWTProcessor<SecurityContext> processor, Set<String> issuers, Set<String> audiences) {}

	private final Map<OauthProvider, Rule> rules = new EnumMap<>(OauthProvider.class);

	public OauthIdTokenVerifier(
			@Value("${oauth.google.client-ids:}") List<String> googleClientIds,
			@Value("${oauth.apple.client-id:}") String appleClientId) {
		rules.put(OauthProvider.GOOGLE, rule(
				"https://www.googleapis.com/oauth2/v3/certs",
				Set.of("https://accounts.google.com", "accounts.google.com"),
				nonBlank(googleClientIds)));
		rules.put(OauthProvider.APPLE, rule(
				"https://appleid.apple.com/auth/keys",
				Set.of("https://appleid.apple.com"),
				nonBlank(List.of(appleClientId))));
	}

	public Identity verify(OauthProvider provider, String idToken) {
		Rule rule = rules.get(provider);
		try {
			JWTClaimsSet claims = rule.processor().process(idToken, null);
			List<String> audience = claims.getAudience();
			boolean valid = claims.getSubject() != null
					&& rule.issuers().contains(claims.getIssuer())
					&& audience != null
					&& audience.stream().anyMatch(rule.audiences()::contains);
			if (!valid) {
				throw new ApiException(ErrorCode.UNAUTHORIZED);
			}
			return new Identity(claims.getSubject(), claims.getStringClaim("name"));
		} catch (ParseException | BadJOSEException | JOSEException e) {
			throw new ApiException(ErrorCode.UNAUTHORIZED);
		}
	}

	private static Rule rule(String jwksUri, Set<String> issuers, Set<String> audiences) {
		try {
			JWKSource<SecurityContext> keySource =
					JWKSourceBuilder.<SecurityContext>create(URI.create(jwksUri).toURL()).build();
			DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
			processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource));
			return new Rule(processor, issuers, audiences);
		} catch (MalformedURLException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Set<String> nonBlank(List<String> values) {
		return values.stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toUnmodifiableSet());
	}
}
