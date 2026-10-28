package com.tukorea.gdgoc.koready.datalab;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 국가별 월 소비 비율·방문 비율 (전체 외국인 대비).
 */
@Entity
@Table(
		name = "datalab_country_month",
		uniqueConstraints = @UniqueConstraint(name = "uk_datalab_country_month_country_code_ym",
				columnNames = {"country_code", "ym"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DatalabCountryMonth {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 2)
	private String countryCode;

	/** YYYYMM */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 6)
	private String ym;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal cardSpendSharePct;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal visitorSharePct;
}
