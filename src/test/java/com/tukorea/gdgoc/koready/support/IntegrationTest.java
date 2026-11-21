package com.tukorea.gdgoc.koready.support;

import com.tukorea.gdgoc.koready.infra.odsay.OdsayClient;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 Docker DB(덤프 데이터)에 붙는 API 통합 테스트. 테스트마다 롤백하고, "오늘"은 {@link TestClockConfig#TODAY}다.
 * 로그인이 필요한 API는 헤더 {@code X-Dev-User-Id}로 부른다 (test 프로필).
 * ODsay는 부르지 않는다: {@link OdsayClient}는 가짜이고 기본은 키가 없는 것과 같다 (테스트에서 주입받아 정한다).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
@MockitoBean(types = OdsayClient.class)
@Transactional
public @interface IntegrationTest {
}
