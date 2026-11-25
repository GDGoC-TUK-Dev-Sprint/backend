package com.tukorea.gdgoc.koready.domain.exchange.service;

import com.tukorea.gdgoc.koready.domain.exchange.entity.Currency;
import com.tukorea.gdgoc.koready.domain.exchange.entity.ExchangeRate;
import com.tukorea.gdgoc.koready.domain.exchange.repository.ExchangeRateRepository;
import com.tukorea.gdgoc.koready.global.dto.DisplayCurrency;
import com.tukorea.gdgoc.koready.global.dto.Money;
import com.tukorea.gdgoc.koready.global.exception.ApiException;
import com.tukorea.gdgoc.koready.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
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

	/** 환산에 쓰는 환율: 표시 통화, 1단위당 원화(JPY는 1엔 기준), 고시일 */
	public record Rate(DisplayCurrency currency, BigDecimal krwPerUnit, LocalDate rateDate) {
	}

	/** 외화의 오늘 기준 환율 (응답에 기준 환율·고시일을 줄 때). KRW는 환율이 없으므로 부르지 않는다 */
	@Transactional(readOnly = true)
	public Rate rate(DisplayCurrency currency) {
		if (currency == DisplayCurrency.KRW) {
			throw new IllegalArgumentException("KRW는 환율이 없습니다");
		}
		ExchangeRate rate = latest(currency, LocalDate.now(clock));
		return new Rate(currency, rate.getKrwPerUnit(), rate.getRateDate());
	}

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
		return latest(currency, date).getKrwPerUnit();
	}

	/**
	 * 표시 통화 1단위당 원화 (오늘 기준). 환율이 없으면 예외 없이 비어 있다. 환율이 없어도 응답을 줘야 하는 곳(여행 목록)에서
	 * 한 번 읽어 {@link #fromKrw(long, DisplayCurrency, BigDecimal)}에 넘긴다. KRW는 1
	 */
	@Transactional(readOnly = true)
	public Optional<BigDecimal> findKrwPerUnit(DisplayCurrency currency) {
		if (currency == DisplayCurrency.KRW) {
			return Optional.of(BigDecimal.ONE);
		}
		return findLatest(currency, LocalDate.now(clock)).map(ExchangeRate::getKrwPerUnit);
	}

	/**
	 * 요청 금액(희망 예산·실제 지출)을 원 단위로 (오늘 환율, 반올림). 소수 자리 검사는 부르는 쪽이 필드 오류로 먼저 한다.
	 */
	@Transactional(readOnly = true)
	public long toKrw(Money money) {
		return toKrw(money, krwPerUnit(money.currency()));
	}

	/** 요청 금액을 이미 읽어 둔 환율로 원 단위로 바꾼다 (반올림). 여러 금액을 바꿀 때 환율을 한 번만 읽는다 */
	public long toKrw(Money money, BigDecimal krwPerUnit) {
		return money.amount().multiply(krwPerUnit).setScale(0, RoundingMode.HALF_UP).longValueExact();
	}

	/** 원화 금액 하나를 표시 통화로 (오늘 환율, 반올림). 범위(최소~최대)는 최소 내림·최대 올림으로 따로 바꾼다 */
	@Transactional(readOnly = true)
	public Money fromKrw(long krw, DisplayCurrency currency) {
		if (currency == DisplayCurrency.KRW) {
			return Money.krw(krw);
		}
		return fromKrw(krw, currency, krwPerUnit(currency));
	}

	/** 원화 금액 하나를 이미 읽어 둔 환율로 표시 통화로 바꾼다 (반올림). 여러 금액을 바꿀 때 환율을 한 번만 읽는다 */
	public Money fromKrw(long krw, DisplayCurrency currency, BigDecimal krwPerUnit) {
		if (currency == DisplayCurrency.KRW) {
			return Money.krw(krw);
		}
		int scale = (currency == DisplayCurrency.USD || currency == DisplayCurrency.EUR) ? 2 : 0;
		return new Money(BigDecimal.valueOf(krw).divide(krwPerUnit, scale, RoundingMode.HALF_UP), currency);
	}

	/** 그날 이전(당일 포함) 가장 최근 고시 환율. 없으면 비어 있다 */
	private Optional<ExchangeRate> findLatest(DisplayCurrency currency, LocalDate date) {
		return rates.findLatest(Currency.valueOf(currency.name()), date, PageRequest.of(0, 1)).stream().findFirst();
	}

	/** 그날 이전(당일 포함) 가장 최근 고시 환율. 없으면 503 */
	private ExchangeRate latest(DisplayCurrency currency, LocalDate date) {
		return findLatest(currency, date)
				.orElseThrow(() -> new ApiException(ErrorCode.EXCHANGE_RATE_UNAVAILABLE,
						currency + " 환율 데이터가 없어 환산할 수 없습니다"));
	}
}
