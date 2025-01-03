package com.stone.microstone.controller.login;

import com.stone.microstone.dto.soical.ErrorResponse;
import com.stone.microstone.dto.soical.GoogleUserInfo;
import com.stone.microstone.dto.soical.KakaoUserInfo;
import com.stone.microstone.dto.soical.NaverUserInfo;
import com.stone.microstone.service.social.GoogleService;
import com.stone.microstone.service.social.KakaoService;
import com.stone.microstone.service.social.NaverService;
import com.stone.microstone.service.social.SocialUserSerivce;

import com.stone.microstone.domain.entitiy.LocalUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final SocialUserSerivce userSerivce;
    private final KakaoService kakaoService;
    private final NaverService naverService;
    private final GoogleService googleService;

    @Autowired
    public AuthController(SocialUserSerivce userSerivce, KakaoService kakaoService, NaverService naverService, GoogleService googleService){
        this.userSerivce = userSerivce;
        this.kakaoService = kakaoService;
        this.naverService = naverService;
        this.googleService = googleService;
    }

    @DeleteMapping("/Delete")  //db에 등록된 유저 삭제
    public ResponseEntity<String> deleteUser(){
        userSerivce.deleteAllUsers();
        log.info("인포로 만들었어용");
        return ResponseEntity.ok("다 지우기 성공");
    }

    @PostMapping("/kakao-login") //카카오 로그인을 위한 api.
    public ResponseEntity<?> kakaoLogin(@RequestHeader("Authorization") String accesToken, HttpSession session, HttpServletResponse response){
        try{
            KakaoUserInfo userInfo = kakaoService.getUserInfo(accesToken);
            if(userInfo.getEmail()==null ){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(),"이메일이 존재하지 않습니다."));
            }else if(userInfo.getNickname() ==null){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(),"닉네임이 존재하지 않습니다."));
            }
            LocalUser user= userSerivce.findOrCreateUser(userInfo.getEmail(), userInfo.getNickname(), accesToken,"kakao");

            session.setAttribute("userId", user.getId());
            session.setAttribute("userEmail", user.getEmail());
            session.setAttribute("loginType","kakao");


            return ResponseEntity.ok(Map.of("로그인 성공",accesToken,"userId",user.getId()));
        }catch(Exception e){
            ErrorResponse errorResponse=new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),"로그인 실패"+e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/naver-login")  //네이버 로그인을 위한 api
    public ResponseEntity<?> naverLogin(@RequestHeader("Authorization") String accesToken, HttpSession session, HttpServletResponse response){
        try{
            NaverUserInfo userInfo = naverService.getUserInfo(accesToken);
            if(userInfo.getEmail()==null ){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(),"이메일이 존재하지 않습니다."));
            }else if(userInfo.getName() ==null){
                log.info(userInfo.getEmail());
                log.info(userInfo.getName());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(),"닉네임이 존재하지 않습니다."));
            }
            LocalUser user=userSerivce.findOrCreateUser(userInfo.getEmail(), userInfo.getName() , accesToken,"naver");

            session.setAttribute("userId", user.getId());
            session.setAttribute("userEmail", user.getEmail());
            session.setAttribute("loginType","naver");


            return ResponseEntity.ok(Map.of("로그인 성공",accesToken,"userId",user.getId()));
        }catch(Exception e){
            ErrorResponse errorResponse=new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),"로그인 실패"+e.getMessage()); // 예외 스택 트레이스를 출력하여 디버깅에 도움
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/google-login")//구글 로그인을 위한 api
    public ResponseEntity<?> googleLogin(@RequestHeader("Authorization") String accesToken, HttpSession session, HttpServletResponse response){
        try{

            GoogleUserInfo userInfo = googleService.getUserInfo(accesToken);
            if(userInfo.getEmail()==null ){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(),"이메일이 존재하지 않습니다."));
            }else if(userInfo.getName() ==null){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse(HttpStatus.UNAUTHORIZED.value(),"닉네임이 존재하지 않습니다."));
            }
            LocalUser user= userSerivce.findOrCreateUser(userInfo.getEmail(), userInfo.getName(), accesToken,"google");

            session.setAttribute("userId", user.getId());
            session.setAttribute("userEmail", user.getEmail());
            session.setAttribute("loginType","google");


            return ResponseEntity.ok(Map.of("로그인 성공",accesToken,"userId",user.getId()));
        }catch(Exception e){
            ErrorResponse errorResponse=new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),"로그인 실패"+e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

}
