package com.tukorea.gdgoc.koready.place;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

/**
 * TourAPI 신분류 코드표 (대·중·소 3단계).
 */
@Entity
@Table(name = "place_classes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceClass {

	@Id
	@Column(length = 8)
	private String code;

	@Column(nullable = false, check = @CheckConstraint(name = "ck_place_classes_level", constraint = "level between 1 and 3"))
	private Short level;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_code", foreignKey = @ForeignKey(name = "fk_place_classes_parent_code"))
	private PlaceClass parent;

	@Column(nullable = false, length = 50)
	private String nameKo;

	@Column(length = 100)
	private String nameEn;

	@ColumnDefault("false")
	@Column(nullable = false)
	private boolean excluded;
}
