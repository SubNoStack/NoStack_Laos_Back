package com.stone.microstone.repository.social;

import com.stone.microstone.domain.entitiy.LocalUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;


import java.util.List;
import java.util.Optional;

//유저 테이블에 접근하는 구현체 클래스
public class SocialUserRepositoryImpl implements SocialUserRepository {

    //직접 엔티티들을 사용.관리하는 엔티티 매니저
    @PersistenceContext
    private EntityManager em;

    public SocialUserRepositoryImpl(EntityManager em) {
        this.em = em;
    }

    //유저 저장을 수행하는 메소드
    @Override
    public LocalUser save(LocalUser user) {
        em.persist(user);
        return user;
    }

    //유저의 이름을 사용해 유저를 조회하는 메소드.여러명 조회후 제일위에 있는 사람만 반환
    @Override
    public Optional<LocalUser> findByUsername(String username) {
        List<LocalUser> result = em.createQuery("SELECT u FROM LocalUser u WHERE u.name = :username", LocalUser.class)
                .setParameter("username", username)
                .getResultList();
        return result.stream().findFirst();
    }

    //이메일을 이용해 유저를 조회하는 메소드
    @Override
    public Optional<LocalUser> findByEmail(String email) {
        List<LocalUser> result = em.createQuery("SELECT u FROM LocalUser u WHERE u.email = :email", LocalUser.class)
                .setParameter("email", email)
                .getResultList();
        return result.stream().findFirst();
    }

    //이메일과 회원가입 경로 정보를 이용해 유저를 조회하는 메소드
    @Override
    public Optional<LocalUser> findByLoginInfo(String email, String loginInfo) {
        List<LocalUser> result=em.createQuery("SELECT u FROM LocalUser u WHERE u.email= : email AND u.loginInfo = :loginInfo", LocalUser.class)
                .setParameter("email",email)
                .setParameter("loginInfo",loginInfo)
                .getResultList();
        return result.stream().findFirst();
    }


    //전체 유저를 삭제하는 메소드.
    @Override
    public void deleteAll() {
        em.createQuery("DELETE FROM LocalUser").executeUpdate();
        em.createNativeQuery("ALTER TABLE user AUTO_INCREMENT = 1").executeUpdate();
    }
}


