package com.tukorea.gdgoc.koready.place;

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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 장소의 대상별 요금.
 */
@Entity
@Table(
		name = "place_fees",
		indexes = @Index(name = "idx_place_fees_place_id", columnList = "place_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceFee {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "place_id", nullable = false, foreignKey = @ForeignKey(name = "fk_place_fees_place_id"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Place place;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private FeeAudience audience;

	@Column(nullable = false, length = 100)
	private String labelKo;

	@Column(length = 150)
	private String labelEn;

	/** 0 = 무료 */
	@Column(nullable = false,
			check = @CheckConstraint(name = "ck_place_fees_amount_krw", constraint = "amount_krw >= 0"))
	private Integer amountKrw;
}
