package com.tukorea.gdgoc.koready.domain.exchange.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 환율 수집 스케줄({@code @Scheduled})을 켠다.
 * 적재 모드(loader)는 적재가 끝나면 종료해야 해서 켜지 않는다 (스케줄러 스레드가 있으면 JVM이 끝나지 않는다).
 */
@Configuration
@Profile("!loader")
@EnableScheduling
class ExchangeConfig {
}
