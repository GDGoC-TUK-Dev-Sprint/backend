package com.tukorea.gdgoc.koready.trip;

import com.tukorea.gdgoc.koready.place.PlaceClass;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행에서 고른 카테고리 (대분류). Trip의 컬렉션으로 trip_categories에 저장된다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "class1Code")
public class TripCategory {

	@Column(name = "class1_code", nullable = false, length = 2)
	private String class1Code;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "class1_code", nullable = false, insertable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_trip_categories_class1_code"))
	private PlaceClass placeClass;

	TripCategory(String class1Code) {
		this.class1Code = class1Code;
	}
}
