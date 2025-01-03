package com.stone.microstone.domain.entitiy;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

//문제집 db 엔티티
@Entity
@Table(name="workbook")
@Data
public class WorkBook {
    //저장된 문제집 기본키
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int wb_id;

    //문제집 이름
    @Column
    private String wb_title;

    //문제집 생성된 전체 내용
    @Column(columnDefinition = "MEDIUMTEXT")
    private String wb_content;

    //문제집 관련 요약 내용
    @Column(columnDefinition = "MEDIUMTEXT")
    private String wb_sumtext;

    //문제집 생성날짜
    @Column
    private LocalDate wb_create;

    //문제집 즐겨찾기 여부
    @Column
    private boolean wb_favorite;

    //문제집 답지 내용
    @Column(columnDefinition = "MEDIUMTEXT")
    private  String wb_answer;

    //개인이 생성한 문제집 번호
    @Column
    private int wb_user_id;

    //답지 즐겨찾기 여부
    @Column
    private boolean wb_favorite_answer;

    //답지 제목
    @Column
    private String wb_title_answer;

    //테이블간 관계 작성
    @ManyToOne
    @JoinColumn(name="user_id",referencedColumnName = "user_id",nullable = false)
    private LocalUser user;

    @OneToOne(mappedBy = "workBook",cascade = CascadeType.ALL,fetch = FetchType.LAZY)
    private WorkBookPDF workBookPDF;

    @OneToOne(mappedBy = "workBook",cascade = CascadeType.ALL,fetch = FetchType.LAZY)
    private AnswerPDF answerPDF;

}