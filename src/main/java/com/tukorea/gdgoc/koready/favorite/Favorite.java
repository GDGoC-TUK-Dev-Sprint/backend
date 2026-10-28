package com.tukorea.gdgoc.koready.favorite;

import com.tukorea.gdgoc.koready.place.Place;
import com.tukorea.gdgoc.koready.user.User;
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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * 찜한 장소. 사용자·장소마다 1행.
 */
@Entity
@Table(
		name = "favorites",
		uniqueConstraints = @UniqueConstraint(name = "uk_favorites_user_id_place_id", columnNames = {"user_id", "place_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Favorite {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_favorites_user_id"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "place_id", nullable = false, foreignKey = @ForeignKey(name = "fk_favorites_place_id"))
	private Place place;

	/** 찜한 시각 */
	@Column(nullable = false)
	private Instant createdAt;
}
