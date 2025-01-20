package com.stone.microstone.domain.entitiy;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

//문제집 db 엔티티
@Entity
@Table(name="workbook")
@Data
public class WorkBook {
    //저장된 문제집 기본키
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private int wb_id;

    @Column
    private String wb_title;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String wb_content;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String wb_sumtext;

    @Column
    private LocalDate wb_create;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String wb_answer;

    @Column(columnDefinition = "VARCHAR(45)")
    private String wb_language;

    @Column(columnDefinition = "VARCHAR(45)")
    private String wb_category;

    @Column
    private String wb_title_answer;

    @OneToOne(mappedBy = "workBook",cascade = CascadeType.ALL,fetch = FetchType.LAZY)
    private WorkBookPDF workBookPDF;

    @OneToOne(mappedBy = "workBook",cascade = CascadeType.ALL,fetch = FetchType.LAZY)
    private AnswerPDF answerPDF;

    @OneToMany(mappedBy = "workBook")
    private List<Question> questions;

}