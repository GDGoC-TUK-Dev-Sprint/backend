package com.tukorea.gdgoc.koready.trip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 여행의 하루.
 */
@Entity
@Table(
		name = "trip_days",
		uniqueConstraints = @UniqueConstraint(name = "uk_trip_days_trip_id_day_number", columnNames = {"trip_id", "day_number"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripDay {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_id", nullable = false, foreignKey = @ForeignKey(name = "fk_trip_days_trip_id"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Trip trip;

	/** 1부터 */
	@Column(nullable = false)
	private Short dayNumber;

	@Column(nullable = false)
	private LocalDate tripDate;

	/** 숙소 출발 */
	@Column(columnDefinition = "time")
	private LocalTime departTime;

	/** 숙소 도착 */
	@Column(columnDefinition = "time")
	private LocalTime returnTime;
}
