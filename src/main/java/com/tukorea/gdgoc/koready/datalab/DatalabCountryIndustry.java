package com.tukorea.gdgoc.koready.datalab;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 국가별 월 업종 소비 비율.
 */
@Entity
@Table(
		name = "datalab_country_industry",
		uniqueConstraints = @UniqueConstraint(name = "uk_datalab_country_industry_country_code_ym_industry",
				columnNames = {"country_code", "ym", "industry"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DatalabCountryIndustry {

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

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Industry industry;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal sharePct;
}
