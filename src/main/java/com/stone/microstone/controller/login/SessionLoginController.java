package com.stone.microstone.controller.login;

import com.stone.microstone.dto.local.JoinRequest;
import com.stone.microstone.dto.local.LoginRequest;
import com.stone.microstone.domain.entitiy.LocalUser;
import com.stone.microstone.service.social.LocalUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class SessionLoginController {

    private final LocalUserService userService;

    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmailDuplicate(@RequestParam("email") String email) {
        boolean isDuplicate = userService.checkEmailDuplicate(email);
        if (isDuplicate) {
            return ResponseEntity.badRequest().body("{\"message\": \"사용중인 이메일입니다.\"}");
        }
        return ResponseEntity.ok("{\"message\": \"사용가능한 이메일입니다.\"}");
    }


    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestBody JoinRequest joinRequest) {
        try {
            userService.join(joinRequest);
            return ResponseEntity.ok("{\"message\": \"User registered successfully\"}");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "회원정보 저장중 오류가 발생했습니다." + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpSession session) {
        LocalUser user = userService.login(loginRequest);
        if (user == null) {
            return ResponseEntity.badRequest().body("{\"message\": \"유효하지 않은 이메일 또는 패스워드입니다.\"}");
        }
        session.setAttribute("userId", user.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("userId", user.getId());
        response.put("email", user.getEmail());
        response.put("name", user.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok("{\"message\": \"로그아웃 성공했습니다.\"}");
    }

    @GetMapping("/info")
    public ResponseEntity<?> getUserInfo(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body("{\"message\": \"로그인 되지 않았습니다.\"}");
        }

        LocalUser user = userService.getLoginUserById(userId); // Integer 타입 그대로 사용
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("userId", user.getId());
        response.put("email", user.getEmail());
        response.put("name", user.getName());
        response.put("phone", user.getPhone());
        response.put("loginInfo", user.getLoginInfo());
        return ResponseEntity.ok(response);
    }



    @GetMapping("/find-email")
    public ResponseEntity<?> findEmailByPhone(@RequestParam("phone") String phone) {
        Optional<String> email = userService.findEmailByPhone(phone);
        return email.map(e -> ResponseEntity.ok("{\"email\": \"" + e + "\"}"))
                .orElseGet(() -> ResponseEntity.status(404).body("{\"message\": \"유저를 찾을 수 없습니다.\"}"));
    }

    @PostMapping("/find-password")
    public ResponseEntity<?> findUserForPasswordReset(
            @RequestParam("email") String email,
            @RequestParam("phone") String phone,
            @RequestParam("name") String name) {
        Optional<LocalUser> user = userService.findUserForPasswordReset(email, phone, name);
        return user.map(u -> ResponseEntity.ok("{\"userId\": \"" + u.getId() + "\"}"))
                .orElseGet(() -> ResponseEntity.status(404).body("{\"message\": \"유저를 찾을 수 없습니다.\"}"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestParam("userId") Long userId,
            @RequestParam("newPassword") String newPassword) {
        boolean success = userService.resetPassword(userId, newPassword);
        if (success) {
            return ResponseEntity.ok("{\"message\": \"패스워드 재설정에 성공하였습니다.\"}");
        } else {
            return ResponseEntity.status(404).body("{\"message\": \"유저를 찾을 수 없습니다.\"}");
        }
    }
}