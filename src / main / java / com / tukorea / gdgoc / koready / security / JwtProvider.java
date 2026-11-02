package com.tukorea.gdgoc.koready.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Date;

@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-validity-in-seconds}")
    private long accessTokenValidityInSeconds;

    // JWT 액세스 토큰 생성
    public String createAccessToken(String loginId) throws JOSEException {
        // 시크릿 키는 32바이트(256비트) 이상이어야 합니다.
        JWSSigner signer = new MACSigner(secretKey.getBytes());

        Date now = new Date();
        Date validity = new Date(now.getTime() + (accessTokenValidityInSeconds * 1000));

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(loginId)
                .issueTime(now)
                .expirationTime(validity)
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                claimsSet
        );

        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    // JWT 토큰 검증 및 아이디 추출
    public String getLoginIdFromToken(String token) throws ParseException, JOSEException {
        SignedJWT signedJWT = SignedJWT.parse(token);
        JWSVerifier verifier = new MACVerifier(secretKey.getBytes());

        if (signedJWT.verify(verifier)) {
            return signedJWT.getJWTClaimsSet().getSubject();
        }
        throw new RuntimeException("유효하지 않은 JWT 토큰입니다.");
    }
}
