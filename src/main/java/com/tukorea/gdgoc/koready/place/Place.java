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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

/**
 * TourAPI 관광 정보 1건 (숙박 포함).
 */
@Entity
@Table(
		name = "places",
		uniqueConstraints = @UniqueConstraint(name = "uk_places_content_id", columnNames = "content_id"),
		indexes = {
				@Index(name = "idx_places_class1_code", columnList = "class1_code"),
				@Index(name = "idx_places_class2_code", columnList = "class2_code")
		}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place {

	/** TourAPI 콘텐츠 유형: 숙박 */
	public static final short LODGING_CONTENT_TYPE_ID = 32;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20)
	private String contentId;

	@Column(nullable = false)
	private Short contentTypeId;

	@Column(name = "class1_code", nullable = false, length = 2)
	private String class1Code;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "class1_code", insertable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_places_class1_code"))
	private PlaceClass class1;

	@Column(name = "class2_code", nullable = false, length = 4)
	private String class2Code;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "class2_code", insertable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_places_class2_code"))
	private PlaceClass class2;

	@Column(name = "class3_code", nullable = false, length = 8)
	private String class3Code;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "class3_code", insertable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_places_class3_code"))
	private PlaceClass class3;

	@Column(nullable = false, length = 200)
	private String nameKo;

	@Column(length = 300)
	private String nameEn;

	@Column(nullable = false, length = 2)
	private String ldongRegnCd;

	@Column(nullable = false, length = 3)
	private String ldongSignguCd;

	@Column(length = 200)
	private String addr1;

	@Column(length = 200)
	private String addr2;

	/** 경도(x)·위도(y), SRID 4326 */
	@JdbcTypeCode(SqlTypes.GEOGRAPHY)
	@Column(nullable = false, columnDefinition = "geography(Point,4326)")
	private Point location;

	@Column(columnDefinition = "text")
	private String overviewKo;

	@Column(columnDefinition = "text")
	private String overviewEn;

	@Column(length = 300)
	private String summaryKo;

	@Column(length = 300)
	private String summaryEn;

	@Column(length = 500)
	private String imageUrl;

	@Column(length = 500)
	private String thumbnailUrl;

	@Column(length = 10)
	private String imageLicense;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private HoursType hoursType;

	@Column(columnDefinition = "text")
	private String hoursTextKo;

	@Column(columnDefinition = "text")
	private String hoursTextEn;

	@Column(columnDefinition = "text")
	private String restTextKo;

	@Column(columnDefinition = "text")
	private String restTextEn;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private FeeType feeType;

	@Column(columnDefinition = "text")
	private String feeTextKo;

	@Column(columnDefinition = "text")
	private String feeTextEn;

	@Column(nullable = false)
	private Short stayMinutes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private StayBasis stayBasis;

	private LocalDate eventStartDate;

	private LocalDate eventEndDate;

	@ColumnDefault("0")
	@Column(nullable = false)
	private Short signalCount;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String introRaw;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String infoRaw;

	@Column(nullable = false)
	private Instant sourceModifiedAt;

	@Column(nullable = false)
	private Instant syncedAt;

	@ColumnDefault("true")
	@Column(nullable = false)
	private boolean active;

	/** 숙박 장소인지 (숙소로 고를 수 있는 장소) */
	public boolean isLodging() {
		return contentTypeId == LODGING_CONTENT_TYPE_ID;
	}

	/** 주소 (국문). 상세 주소(addr2)가 있으면 띄어 붙인다 */
	public String getAddress() {
		if (addr2 == null || addr2.isBlank()) {
			return addr1;
		}
		return addr1 == null || addr1.isBlank() ? addr2 : addr1 + " " + addr2;
	}
}
