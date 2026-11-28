package com.tukorea.gdgoc.koready.domain.user.repository;

import com.tukorea.gdgoc.koready.domain.user.entity.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 회원 탈퇴용 삭제. 사용자에 딸린 소셜 계정·리프레시 토큰·찜·여행(일자·일정 항목·이동 구간·예상 경비·실제 지출 포함)은
 * DB가 함께 지운다 (ON DELETE CASCADE).
 */
public interface UserWithdrawalRepository extends Repository<User, Long> {

	/** @return 지운 행 수. 0이면 이미 없는 사용자다 */
	@Modifying
	@Query("delete from User u where u.id = :userId")
	int deleteUserById(Long userId);
}
