package com.tukorea.gdgoc.koready.domain.user.repository;

import com.tukorea.gdgoc.koready.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByLoginId(String loginId);

	Optional<User> findByLoginId(String loginId);
}
