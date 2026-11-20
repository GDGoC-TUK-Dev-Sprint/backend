package com.tukorea.gdgoc.koready.domain.exchange.service;

import com.tukorea.gdgoc.koready.domain.exchange.entity.Currency;
import com.tukorea.gdgoc.koready.domain.exchange.repository.ExchangeRateRepository;
import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.dto.Money;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원화 ↔ 표시 통화 환산.
 * <ul>
 *   <li>저장·계산은 원화. 요청 금액은 {@link #toKrw}로 원화로 바꿔 저장하고, 응답 금액은 {@link #fromKrw}로 표시 통화로 바꾼다</li>
 *   <li>환율은 오늘(서울) 이전 가장 최근 고시 매매기준율. 주말·공휴일은 직전 영업일 값</li>
 *   <li>소수 자리: 원·엔 0, 달러·유로 2</li>
 *   <li>KRW는 환율 없이 그대로라 실패하지 않는다. 외화 환율이 하나도 없으면 {@code 503 EXCHANGE_RATE_UNAVAILABLE}</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExchangeService {

	private final ExchangeRateRepository rates;
	private final Clock clock;

	/** 표시 통화 1단위당 원화 (오늘 기준, JPY는 1엔 기준). KRW는 1 */
	@Transactional(readOnly = true)
	public BigDecimal krwPerUnit(DisplayCurrency currency) {
		return krwPerUnit(currency, LocalDate.now(clock));
	}

	/** 표시 통화 1단위당 원화 (그날 기준). KRW는 1 */
	@Transactional(readOnly = true)
	public BigDecimal krwPerUnit(DisplayCurrency currency, LocalDate date) {
		if (currency == DisplayCurrency.KRW) {
			return BigDecimal.ONE;
		}
		return rates.findLatest(Currency.valueOf(currency.name()), date, PageRequest.of(0, 1))
				.stream()
				.findFirst()
				.orElseThrow(() -> new ApiException(ErrorCode.EXCHANGE_RATE_UNAVAILABLE,
						currency + " 환율 데이터가 없어 환산할 수 없습니다"))
				.getKrwPerUnit();
	}

	/**
	 * 요청 금액(희망 예산·실제 지출)을 원 단위로 (오늘 환율, 반올림). 소수 자리 검사는 부르는 쪽이 필드 오류로 먼저 한다.
	 */
	@Transactional(readOnly = true)
	public long toKrw(Money money) {
		BigDecimal krw = money.amount().multiply(krwPerUnit(money.currency()));
		return krw.setScale(0, RoundingMode.HALF_UP).longValueExact();
	}

	/** 원화 금액 하나를 표시 통화로 (오늘 환율, 반올림). 범위(최소~최대)는 최소 내림·최대 올림으로 따로 바꾼다 */
	@Transactional(readOnly = true)
	public Money fromKrw(long krw, DisplayCurrency currency) {
		if (currency == DisplayCurrency.KRW) {
			return Money.krw(krw);
		}
		int scale = (currency == DisplayCurrency.USD || currency == DisplayCurrency.EUR) ? 2 : 0;
		BigDecimal amount = BigDecimal.valueOf(krw).divide(krwPerUnit(currency), scale, RoundingMode.HALF_UP);
		return new Money(amount, currency);
	}
}
