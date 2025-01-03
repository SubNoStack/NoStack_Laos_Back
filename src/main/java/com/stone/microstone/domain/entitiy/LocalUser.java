package com.stone.microstone.domain.entitiy;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "user")
@Getter
@Setter
@NoArgsConstructor
public class LocalUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private int id;

    @Column(name = "user_email", nullable = false)
    private String email;

    @Column(name = "user_name")
    private String name;

    @Column(name = "user_password")
    private String password;

    @Column(name = "user_phone")
    private String phone;  // 변경: Integer -> String

    @Column(name = "user_logininfo")
    private String loginInfo;

    @Column(name = "user_token")
    private String token;

    @OneToMany(mappedBy = "user")
    private List<WorkBook> WorkBooks;

    public void setSocialLogin(String usertoken,
                               String logininfo){
        this.password = null;
        this.token = usertoken;
        this.loginInfo = logininfo;
    }
}