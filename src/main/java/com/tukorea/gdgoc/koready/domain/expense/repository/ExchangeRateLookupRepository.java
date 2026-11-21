package com.tukorea.gdgoc.koready.domain.expense.repository;

import com.tukorea.gdgoc.koready.domain.exchange.entity.Currency;
import com.tukorea.gdgoc.koready.domain.exchange.entity.ExchangeRate;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 표시 통화 환산용 환율 조회 (읽기 전용).
 */
public interface ExchangeRateLookupRepository extends Repository<ExchangeRate, Long> {

	/** 기준일 이전(당일 포함) 가장 최근 고시 환율. 환율은 영업일만 있어서 주말·공휴일에는 직전 영업일 값을 쓴다 */
	@Query("select r from ExchangeRate r where r.currency = :currency and r.rateDate <= :date order by r.rateDate desc")
	List<ExchangeRate> findLatest(Currency currency, LocalDate date, Pageable pageable);
}
