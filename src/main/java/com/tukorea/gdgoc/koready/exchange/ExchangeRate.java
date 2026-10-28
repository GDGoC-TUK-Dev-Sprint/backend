package com.tukorea.gdgoc.koready.exchange;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 고시일별 환율 (영업일만 행이 있다). KRW는 저장하지 않는다.
 */
@Entity
@Table(
		name = "exchange_rates",
		uniqueConstraints = @UniqueConstraint(name = "uk_exchange_rates_rate_date_currency", columnNames = {"rate_date", "currency"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private LocalDate rateDate;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 3)
	private Currency currency;

	/** 1단위당 원화 (JPY도 1엔 기준) */
	@Column(nullable = false, precision = 12, scale = 4)
	private BigDecimal krwPerUnit;

	@Column(nullable = false)
	private Instant fetchedAt;
}
