package com.tukorea.gdgoc.koready.place;

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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 장소 인기도 신호. 출처마다 1행.
 */
@Entity
@Table(
		name = "place_signals",
		uniqueConstraints = @UniqueConstraint(name = "uk_place_signals_place_id_signal_type",
				columnNames = {"place_id", "signal_type"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceSignal {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "place_id", nullable = false, foreignKey = @ForeignKey(name = "fk_place_signals_place_id"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Place place;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SignalType signalType;

	private Long value;

	@Column(nullable = false, length = 20)
	private String basePeriod;

	@Column(length = 200)
	private String matchedName;
}
