package com.tukorea.gdgoc.koready.domain.trip.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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

	@OneToMany(mappedBy = "tripDay", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("seq")
	private List<TripItem> items = new ArrayList<>();

	@OneToMany(mappedBy = "tripDay", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("seq")
	private List<Leg> legs = new ArrayList<>();

	/** 일정 항목 (방문 순서). 목록을 직접 바꾸지 않게 읽기 전용으로 준다 */
	public List<TripItem> getItems() {
		return Collections.unmodifiableList(items);
	}

	/** 이동 구간 (숙소 출발 구간부터 숙소 도착 구간까지). 목록을 직접 바꾸지 않게 읽기 전용으로 준다 */
	public List<Leg> getLegs() {
		return Collections.unmodifiableList(legs);
	}

	/** 일정 항목을 뺀다 (장소 삭제). 행은 orphanRemoval로 지워진다 */
	public void removeItem(TripItem item) {
		items.remove(item);
	}

	/** 항목의 seq를 바꾼 뒤 메모리의 목록도 seq 순서로 맞춘다 */
	public void sortItemsBySeq() {
		items.sort(Comparator.comparing(TripItem::getSeq));
	}

	public void addLeg(Leg leg) {
		legs.add(leg);
	}

	/** 이동 구간을 모두 비운다. 행은 orphanRemoval로 지워진다 */
	public void clearLegs() {
		legs.clear();
	}

	/** 숙소 출발·도착 시각을 기록한다. 항목이 없으면 둘 다 null */
	public void updateTimes(LocalTime departTime, LocalTime returnTime) {
		this.departTime = departTime;
		this.returnTime = returnTime;
	}
}
