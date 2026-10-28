package com.tukorea.gdgoc.koready.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

/**
 * 리프레시 토큰. 토큰 값 대신 해시를 저장하고, 로그인한 기기마다 1행.
 */
@Entity
@Table(
		name = "refresh_tokens",
		uniqueConstraints = @UniqueConstraint(name = "uk_refresh_tokens_token_hash", columnNames = "token_hash"),
		indexes = @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_refresh_tokens_user_id"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	/** SHA-256 (16진수 64자) */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private Instant createdAt;
}
