package com.stone.microstone.domain.entitiy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

//회원가입한 유저를 저장하는 db엔티티 클래스
@Entity
@Table(name ="user")
@Getter
@Setter
public class SocialUser {

    //기본키
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int user_id;

    //유저 이메일
    @Column(nullable = false)
    private String user_email;

    //유저 이름
    @Column
    private String user_name;

    //유저 비밀번호.로컬로그인시만 저장
    @Column
    private String user_password;

    //유저 핸드폰 번호
    @Column
    private String user_phone;

    //유저의 회원가입 경로.로컬인지 소셜로그인인지 구분
    @Column
    private String user_logininfo;

    //유저에 oauth토큰 저장.
    @Column
    private String user_token;

//    @OneToMany(mappedBy = "user")
//    private List<workbook> workbooks;

    public void setLocalLogin(String password){
        this.user_password = password;
        this.user_token = null;
        this.user_logininfo = "local";
    }

    public void setSocialLogin(String usertoken,
                               String logininfo){
        this.user_password = null;
        this.user_token = usertoken;
        this.user_logininfo = logininfo;
    }
}
