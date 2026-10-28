package com.tukorea.gdgoc.koready.trip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 연속한 두 지점 사이의 이동 구간. 숙소 출발·도착 구간도 포함한다 (from/to가 NULL이면 숙소).
 */
@Entity
@Table(
		name = "legs",
		uniqueConstraints = @UniqueConstraint(name = "uk_legs_trip_day_id_seq", columnNames = {"trip_day_id", "seq"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Leg {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_day_id", nullable = false, foreignKey = @ForeignKey(name = "fk_legs_trip_day_id"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private TripDay tripDay;

	/** 0 = 숙소 → 첫 장소 */
	@Column(nullable = false)
	private Short seq;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "from_item_id", foreignKey = @ForeignKey(name = "fk_legs_from_item_id"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private TripItem fromItem;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "to_item_id", foreignKey = @ForeignKey(name = "fk_legs_to_item_id"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private TripItem toItem;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private LegMode mode;

	@Column(name = "distance_m", nullable = false)
	private Integer distanceM;

	@Column(nullable = false)
	private Short durationMinutes;

	/** 1인 운임. 도보는 0, 운임을 받지 못한 구간은 NULL */
	private Integer fareKrw;

	/** 좌표로 추정한 값이면 true, 경로 API 결과면 false */
	@ColumnDefault("true")
	@Column(nullable = false)
	private boolean estimated = true;
}
