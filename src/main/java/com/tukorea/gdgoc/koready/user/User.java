package com.tukorea.gdgoc.koready.user;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자. 아이디·비밀번호 해시는 아이디 계정만 채운다 (소셜 계정은 NULL).
 */
@Entity
@Table(
		name = "users",
		uniqueConstraints = @UniqueConstraint(name = "uk_users_login_id", columnNames = "login_id"),
		check = {
				@CheckConstraint(name = "ck_users_login_id_password_hash",
						constraint = "(login_id is null) = (password_hash is null)"),
				@CheckConstraint(name = "ck_users_name",
						constraint = "login_id is null or name is not null")
		}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 30)
	private String loginId;

	/** BCrypt 해시 */
	@Column(length = 100)
	private String passwordHash;

	@Column(length = 50)
	private String name;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;
}
