package com.tukorea.gdgoc.koready.domain.exchange.repository;

import com.tukorea.gdgoc.koready.domain.exchange.entity.Currency;
import com.tukorea.gdgoc.koready.domain.exchange.entity.ExchangeRate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

	/** 기준일 이전(당일 포함) 가장 최근 고시 환율. 환율은 영업일만 있어서 주말·공휴일에는 직전 영업일 값을 쓴다 */
	@Query("select r from ExchangeRate r where r.currency = :currency and r.rateDate <= :date order by r.rateDate desc")
	List<ExchangeRate> findLatest(Currency currency, LocalDate date, Pageable pageable);

	Optional<ExchangeRate> findByRateDateAndCurrency(LocalDate rateDate, Currency currency);

	/** 그 고시일에 저장된 통화 수 (시작 시 채우기에서 빠진 통화가 있는지 본다) */
	long countByRateDate(LocalDate rateDate);
}
