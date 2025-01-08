package com.stone.microstone.repository.social;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocalUserRepository extends JpaRepository<LocalUser, Long> {
    Optional<LocalUser> findByEmail(String email);
    boolean existsByEmail(String email);

    Optional<LocalUser> findByPhone(String phone);
    Optional<LocalUser> findByEmailAndPhoneAndName(String email, String phone, String name);
    Optional<LocalUser> findByEmailAndLoginInfo(String email, String loginInfo);
    Optional<LocalUser> findById(int id);

}