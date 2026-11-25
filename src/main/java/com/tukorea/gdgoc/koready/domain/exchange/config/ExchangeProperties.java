package com.tukorea.gdgoc.koready.domain.exchange.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 환율 수집 설정 ({@code koready.exchange.*}).
 *
 * @param collectCron 오늘 환율을 받는 시각 (서울, Spring cron 6자리). 고시는 영업일 오전 11시쯤이다
 * @param backfillDays 서버를 켤 때 오늘부터 거슬러 올라가 비어 있는 날을 채우는 일수 (0이면 채우지 않는다)
 */
@Validated
@ConfigurationProperties("koready.exchange")
public record ExchangeProperties(@NotBlank String collectCron, @Min(0) @Max(31) int backfillDays) {
}
