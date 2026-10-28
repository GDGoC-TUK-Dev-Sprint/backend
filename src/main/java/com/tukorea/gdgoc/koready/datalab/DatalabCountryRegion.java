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
 * 국가별 시도 소비 비율·방문 비율.
 */
@Entity
@Table(
		name = "datalab_country_region",
		uniqueConstraints = @UniqueConstraint(name = "uk_datalab_country_region_country_code_period_from_sido_name",
				columnNames = {"country_code", "period_from", "sido_name"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DatalabCountryRegion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 2)
	private String countryCode;

	/** YYYYMM */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 6)
	private String periodFrom;

	/** YYYYMM */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 6)
	private String periodTo;

	@Column(nullable = false, length = 20)
	private String sidoName;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal spendSharePct;

	@Column(nullable = false, precision = 5, scale = 2)
	private BigDecimal visitSharePct;
}
