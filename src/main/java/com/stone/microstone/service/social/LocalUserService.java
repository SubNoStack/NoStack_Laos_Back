package com.stone.microstone.service.social;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.stone.microstone.dto.local.JoinRequest;
import com.stone.microstone.dto.local.LoginRequest;
import com.stone.microstone.domain.entitiy.LocalUser;
import com.stone.microstone.repository.social.LocalUserRepository;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class LocalUserService {

    private final LocalUserRepository userRepository;
    private final BCryptPasswordEncoder encoder;

    public boolean checkEmailDuplicate(String email) {
        return userRepository.existsByEmail(email);
    }

    public void join(JoinRequest req) {
        LocalUser user = req.toEntity();
        user.setPassword(encoder.encode(req.getPassword()));
        userRepository.save(user);
    }

    public LocalUser login(LoginRequest req) {
        Optional<LocalUser> optionalUser = userRepository.findByEmail(req.getEmail());

        if (optionalUser.isEmpty()) {
            return null;
        }

        LocalUser user = optionalUser.get();

        if (!encoder.matches(req.getPassword(), user.getPassword())) {
            return null;
        }

        user.setLoginInfo("Local");
        user.setToken(UUID.randomUUID().toString());
        return userRepository.save(user);
    }

    public LocalUser getLoginUserById(Integer userId) {
        return userRepository.findById(userId).orElse(null);
    }

    // 전화번호로 아이디 찾기
    public Optional<String> findEmailByPhone(String phone) {
        return userRepository.findByPhone(phone).map(LocalUser::getEmail);
    }

    // 이메일, 전화번호, 이름으로 비밀번호 찾기
    public Optional<LocalUser> findUserForPasswordReset(String email, String phone, String name) {
        return userRepository.findByEmailAndPhoneAndName(email, phone, name);
    }

    // 비밀번호 재설정
    public boolean resetPassword(Long userId, String newPassword) {
        Optional<LocalUser> optionalUser = userRepository.findById(userId);
        if (optionalUser.isPresent()) {
            LocalUser user = optionalUser.get();
            user.setPassword(encoder.encode(newPassword));
            userRepository.save(user);
            return true;
        }
        return false;
    }
}