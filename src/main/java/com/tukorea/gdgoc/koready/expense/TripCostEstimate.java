package com.tukorea.gdgoc.koready.expense;

import com.tukorea.gdgoc.koready.trip.Trip;
import jakarta.persistence.CheckConstraint;
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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 여행을 확정할 때 저장한 항목별 1인 예상 경비 범위.
 */
@Entity
@Table(
		name = "trip_cost_estimates",
		uniqueConstraints = @UniqueConstraint(name = "uk_trip_cost_estimates_trip_id_item", columnNames = {"trip_id", "item"}),
		check = @CheckConstraint(name = "ck_trip_cost_estimates_max_krw", constraint = "max_krw >= min_krw")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripCostEstimate {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_id", nullable = false, foreignKey = @ForeignKey(name = "fk_trip_cost_estimates_trip_id"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Trip trip;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private ExpenseItem item;

	@Column(nullable = false,
			check = @CheckConstraint(name = "ck_trip_cost_estimates_min_krw", constraint = "min_krw >= 0"))
	private Integer minKrw;

	@Column(nullable = false)
	private Integer maxKrw;

	/** 확정한 시각 */
	@CreationTimestamp
	@Column(nullable = false)
	private Instant createdAt;
}
