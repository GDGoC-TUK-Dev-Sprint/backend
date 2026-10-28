package com.tukorea.gdgoc.koready.datalab;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 전체 외국인 월별 신용카드 관광소비액·방문자 수.
 */
@Entity
@Table(
		name = "datalab_month_totals",
		uniqueConstraints = @UniqueConstraint(name = "uk_datalab_month_totals_ym", columnNames = "ym")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DatalabMonthTotal {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** YYYYMM */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 6)
	private String ym;

	@Column(nullable = false)
	private Long cardSpendKrw;

	@Column(nullable = false)
	private Long visitors;
}
