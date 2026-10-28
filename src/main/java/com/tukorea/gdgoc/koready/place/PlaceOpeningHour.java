package com.tukorea.gdgoc.koready.place;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

/**
 * 장소의 요일별 운영시간. 행이 없는 요일은 정기 휴무다.
 */
@Entity
@Table(
		name = "place_opening_hours",
		indexes = @Index(name = "idx_place_opening_hours_place_id", columnList = "place_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceOpeningHour {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "place_id", nullable = false, foreignKey = @ForeignKey(name = "fk_place_opening_hours_place_id"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Place place;

	/** 1 월 … 7 일 */
	@Column(nullable = false,
			check = @CheckConstraint(name = "ck_place_opening_hours_day_of_week", constraint = "day_of_week between 1 and 7"))
	private Short dayOfWeek;

	/** MM-DD, NULL이면 연중 */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 5)
	private String seasonFrom;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 5)
	private String seasonTo;

	@Column(nullable = false, columnDefinition = "time")
	private LocalTime openTime;

	/** 자정을 넘기면 openTime보다 작다 */
	@Column(nullable = false, columnDefinition = "time")
	private LocalTime closeTime;

	@Column(columnDefinition = "time")
	private LocalTime lastEntryTime;

	@Column(columnDefinition = "time")
	private LocalTime breakStart;

	@Column(columnDefinition = "time")
	private LocalTime breakEnd;
}
