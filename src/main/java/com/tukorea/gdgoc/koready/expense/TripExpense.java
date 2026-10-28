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
 * 여행 후 입력한 항목별 실제 지출 (전체 인원 합계, 원화). 입력하지 않은 항목은 행이 없다.
 */
@Entity
@Table(
		name = "trip_expenses",
		indexes = @Index(name = "idx_trip_expenses_trip_id", columnList = "trip_id"),
		check = @CheckConstraint(name = "ck_trip_expenses_label",
				constraint = "(item = 'OTHER' and label is not null) or (item <> 'OTHER' and label is null)")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripExpense {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_id", nullable = false, foreignKey = @ForeignKey(name = "fk_trip_expenses_trip_id"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Trip trip;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private ExpenseItem item;

	/** 기타 항목 이름 (OTHER일 때만) */
	@Column(length = 50)
	private String label;

	@Column(nullable = false,
			check = @CheckConstraint(name = "ck_trip_expenses_amount_krw", constraint = "amount_krw >= 0"))
	private Integer amountKrw;

	/** 표시 순서 (기타 항목이 여럿일 때) */
	@Column(nullable = false)
	private Short seq;
}
