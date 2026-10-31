package com.tukorea.gdgoc.koready.trip;

import com.tukorea.gdgoc.koready.place.Place;
import com.tukorea.gdgoc.koready.user.User;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

/**
 * 조건 입력 1회로 만든 여행. 진행 예정·진행 중·완료는 저장하지 않고 날짜로 계산한다.
 */
@Entity
@Table(
		name = "trips",
		indexes = @Index(name = "idx_trips_user_id_end_date", columnList = "user_id, end_date"),
		check = @CheckConstraint(name = "ck_trips_end_date", constraint = "end_date >= start_date")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip {

	/** {@code stay_address} 길이. 장소 주소(addr1 + addr2, 각 200자)는 이보다 길 수 있어 잘라 저장한다 */
	static final int STAY_ADDRESS_LENGTH = 300;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_trips_user_id"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	@Enumerated(EnumType.STRING)
	@ColumnDefault("'DRAFT'")
	@Column(nullable = false, length = 10)
	private TripStatus status = TripStatus.DRAFT;

	@Column(length = 100)
	private String titleKo;

	@Column(length = 150)
	private String titleEn;

	/** ISO 3166-1 alpha-2 */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 2)
	private String nationalityCode;

	@Column(nullable = false)
	private LocalDate startDate;

	@Column(nullable = false)
	private LocalDate endDate;

	@Column(nullable = false,
			check = @CheckConstraint(name = "ck_trips_party_size", constraint = "party_size >= 1"))
	private Short partySize;

	/** 숙박·항공 제외 */
	@Column(check = @CheckConstraint(name = "ck_trips_budget_per_person_krw", constraint = "budget_per_person_krw >= 0"))
	private Integer budgetPerPersonKrw;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private StayType stayType;

	/** stayType = PLACE일 때 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "stay_place_id", foreignKey = @ForeignKey(name = "fk_trips_stay_place_id"))
	private Place stayPlace;

	/** stayType = AREA일 때 */
	@Column(length = 20)
	private String stayAreaCode;

	@Column(nullable = false, length = 200)
	private String stayName;

	@Column(length = STAY_ADDRESS_LENGTH)
	private String stayAddress;

	/** 동선의 출발·도착점. 경도(x)·위도(y), SRID 4326 */
	@JdbcTypeCode(SqlTypes.GEOGRAPHY)
	@Column(nullable = false, columnDefinition = "geography(Point,4326)")
	private Point stayLocation;

	private Instant confirmedAt;

	@CreationTimestamp
	@Column(nullable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	@ElementCollection
	@CollectionTable(name = "trip_categories",
			joinColumns = @JoinColumn(name = "trip_id", foreignKey = @ForeignKey(name = "fk_trip_categories_trip_id")))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TripCategory> categories = new HashSet<>();

	/** 확정 전(DRAFT) 여행을 만든다 */
	static Trip create(User user, TripConditions conditions) {
		Trip trip = new Trip();
		trip.user = user;
		trip.apply(conditions);
		return trip;
	}

	/** 여행 일수 (2박 3일이면 3) */
	public int getDays() {
		return countDays(startDate, endDate);
	}

	/** 시작일부터 종료일까지 날 수 (같은 날이면 1). 저장 전 요청 검사도 이것으로 센다 */
	static int countDays(LocalDate startDate, LocalDate endDate) {
		return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
	}

	/** 확정한 여행은 편집할 수 없다 */
	public boolean isEditable() {
		return status == TripStatus.DRAFT;
	}

	/** 그날(서울 날짜) 기준 진행 상태 */
	public TripProgress progressOn(LocalDate today) {
		if (today.isBefore(startDate)) {
			return TripProgress.UPCOMING;
		}
		return today.isAfter(endDate) ? TripProgress.COMPLETED : TripProgress.ONGOING;
	}

	private void apply(TripConditions conditions) {
		nationalityCode = conditions.nationality().name();
		startDate = conditions.startDate();
		endDate = conditions.endDate();
		partySize = (short) conditions.partySize();
		budgetPerPersonKrw = conditions.budgetPerPersonKrw();
		TripConditions.Stay stay = conditions.stay();
		stayType = stay.type();
		stayPlace = stay.place();
		stayAreaCode = stay.area() == null ? null : stay.area().name();
		stayName = stay.name();
		stayAddress = truncate(stay.address(), STAY_ADDRESS_LENGTH);
		stayLocation = stay.location();
		categories.clear();
		conditions.categories().forEach(category -> categories.add(new TripCategory(category.name())));
	}

	/** 글자 수로 자른다. PostgreSQL varchar(n)이 글자(코드 포인트) 수로 세므로 UTF-16 두 칸인 글자를 반으로 가르지 않는다 */
	private static String truncate(String value, int maxLength) {
		if (value == null || value.codePointCount(0, value.length()) <= maxLength) {
			return value;
		}
		return value.substring(0, value.offsetByCodePoints(0, maxLength));
	}
}
