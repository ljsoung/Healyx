package com.smu.healyx.user.repository;

import com.smu.healyx.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameAndEmail(String username, String email);
    Optional<User> findByRealNameAndEmail(String realName, String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    // 전체 사용자 만 나이 일괄 갱신 (매년 1월 1일 배치용)
    @Modifying
    @Query(value = "UPDATE users SET age = TIMESTAMPDIFF(YEAR, birth_date, CURDATE()) WHERE birth_date IS NOT NULL", nativeQuery = true)
    int updateAllAges();
}