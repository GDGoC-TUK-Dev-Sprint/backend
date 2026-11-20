package com.tukorea.gdgoc.koready.domain.exchange.service;

import com.tukorea.gdgoc.koready.domain.exchange.config.ExchangeProperties;
import com.tukorea.gdgoc.koready.domain.exchange.entity.Currency;
import com.tukorea.gdgoc.koready.domain.exchange.entity.ExchangeRate;
import com.tukorea.gdgoc.koready.domain.exchange.repository.ExchangeRateRepository;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.infra.koreaexim.KoreaEximClient;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 한국수출입은행 환율을 받아 {@code exchange_rates}에 저장한다.
 * <ul>
 *   <li>매일 정해진 시각({@code koready.exchange.collect-cron})에 오늘 환율을 받는다</li>
 *   <li>서버를 켤 때 최근 {@code backfill-days}일 중 비어 있는 날을 채운다 (주말·공휴일은 받아도 비어 있다)</li>
 * </ul>
 * 인증키가 없으면 아무것도 하지 않는다 (테스트·로컬). 수집에 실패해도 서버는 계속 돌고 로그만 남는다.
 * 적재 모드(loader)에서는 돌지 않는다.
 */
@Slf4j
@Component
@Profile("!loader")
@RequiredArgsConstructor
public class ExchangeRateCollector {

	private final KoreaEximClient client;
	private final ExchangeRateRepository rates;
	private final ExchangeProperties properties;
	private final Clock clock;

	@Scheduled(cron = "${koready.exchange.collect-cron}", zone = "Asia/Seoul")
	public void collectToday() {
		collect(LocalDate.now(clock));
	}

	@EventListener(ApplicationReadyEvent.class)
	public void backfill() {
		if (!client.isConfigured()) {
			log.warn("한국수출입은행 인증키(KOREAEXIM_API_KEY)가 없어 환율을 수집하지 않습니다");
			return;
		}
		LocalDate today = LocalDate.now(clock);
		for (int daysAgo = properties.backfillDays() - 1; daysAgo >= 0; daysAgo--) {
			LocalDate date = today.minusDays(daysAgo);
			if (!rates.existsByRateDate(date)) {
				collect(date);
			}
		}
	}

	/** 그 고시일의 환율을 받아 저장(이미 있으면 갱신)하고, 저장한 통화 수를 돌려준다. 실패하면 0 */
	public int collect(LocalDate date) {
		if (!client.isConfigured()) {
			return 0;
		}
		Map<String, BigDecimal> fetched;
		try {
			fetched = client.fetchRates(date);
		} catch (ApiException e) {
			log.warn("{} 환율 수집 실패: {}", date, e.getMessage(), e);
			return 0;
		}
		Instant now = clock.instant();
		int saved = 0;
		for (Currency currency : Currency.values()) {
			BigDecimal krwPerUnit = fetched.get(currency.name());
			if (krwPerUnit == null) {
				continue;
			}
			ExchangeRate rate = rates.findByRateDateAndCurrency(date, currency)
					.map(existing -> {
						existing.update(krwPerUnit, now);
						return existing;
					})
					.orElseGet(() -> ExchangeRate.of(date, currency, krwPerUnit, now));
			rates.save(rate);
			saved++;
		}
		if (saved > 0) {
			log.info("{} 환율 {}개 저장", date, saved);
		}
		return saved;
	}
}
