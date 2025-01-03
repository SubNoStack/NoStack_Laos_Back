package com.stone.microstone.config;

import com.stone.microstone.repository.social.SocialUserRepository;
import com.stone.microstone.repository.social.SocialUserRepositoryImpl;

import com.stone.microstone.service.social.SocialUserSerivce;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AutoConfig {

    @PersistenceContext
    private EntityManager em;

    @Bean
    public SocialUserRepository SocialuserRepository(){
        return new SocialUserRepositoryImpl(em);
    }

    @Bean
    public SocialUserSerivce socialUserService(SocialUserRepository userRepository) {
        return new SocialUserSerivce(userRepository);
    }

//    @Bean
//    public AuthController authController() {}
}
