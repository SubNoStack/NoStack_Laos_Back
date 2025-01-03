package com.stone.microstone.repository.social;

import com.stone.microstone.domain.entitiy.LocalUser;


import java.util.Optional;

//유저 테이블에 직접적으로 접근하는 인터페이스
public interface SocialUserRepository {
    LocalUser save(LocalUser user);

    Optional<LocalUser> findByUsername(String username);

    Optional<LocalUser> findByEmail(String email);

    Optional<LocalUser> findByLoginInfo(String email, String loginInfo);
    void deleteAll();
}
