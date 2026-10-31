package com.tukorea.gdgoc.koready.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 Docker DB(덤프 데이터)에 붙는 API 통합 테스트. 테스트마다 롤백하고, "오늘"은 {@link TestClockConfig#TODAY}다.
 * 로그인이 필요한 API는 헤더 {@code X-Dev-User-Id}로 부른다 (test 프로필).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
@Transactional
public @interface IntegrationTest {
}
