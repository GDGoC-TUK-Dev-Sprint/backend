package com.tukorea.gdgoc.koready.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 소셜 로그인 계정. (provider, subject)로 사용자를 찾는다.
 */
@Entity
@Table(
		name = "oauth_accounts",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_oauth_accounts_user_id_provider", columnNames = {"user_id", "provider"}),
				@UniqueConstraint(name = "uk_oauth_accounts_provider_subject", columnNames = {"provider", "subject"})
		}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OauthAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_oauth_accounts_user_id"))
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private OauthProvider provider;

	/** 제공자 사용자 ID (ID 토큰의 sub) */
	@Column(nullable = false, length = 255)
	private String subject;

	@Column(nullable = false)
	private Instant createdAt;
}
