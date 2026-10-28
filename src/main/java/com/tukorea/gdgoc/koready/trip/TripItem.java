package com.tukorea.gdgoc.koready.trip;

import com.tukorea.gdgoc.koready.place.Place;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 일자에 배치된 장소 1건. 순서를 바꿀 때는 행을 다시 넣지 않고 seq만 고친다
 * (UNIQUE가 보통 제약이라 임시 음수 값을 거친다).
 */
@Entity
@Table(
		name = "trip_items",
		uniqueConstraints = @UniqueConstraint(name = "uk_trip_items_trip_day_id_seq", columnNames = {"trip_day_id", "seq"}),
		indexes = @Index(name = "idx_trip_items_place_id", columnList = "place_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_day_id", nullable = false, foreignKey = @ForeignKey(name = "fk_trip_items_trip_day_id"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private TripDay tripDay;

	/** 방문 순서, 1부터 */
	@Column(nullable = false)
	private Short seq;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "place_id", nullable = false, foreignKey = @ForeignKey(name = "fk_trip_items_place_id"))
	private Place place;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private TripItemOrigin origin;

	/** 식사 자리일 때 */
	@Enumerated(EnumType.STRING)
	@Column(length = 10)
	private MealType mealType;

	@Column(nullable = false, columnDefinition = "time")
	private LocalTime arriveTime;

	@Column(nullable = false, columnDefinition = "time")
	private LocalTime departTime;

	@Column(nullable = false)
	private Short stayMinutes;
}
