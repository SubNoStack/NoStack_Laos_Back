package com.stone.microstone.domain.entitiy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

//답지 pdf저장하는 api
@Entity
@Getter
@Setter
@Table(name = "answerpdf")
public class AnswerPDF {
    //답지 id.기본키
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int answer_pdf_id;

    //답지 이름 저장
    private String fileName;

    //답지 저장한 경로를 확인
    @Column
    private String pdf_path;

    //workbook과 일대일 관계를 이룬다.
    @OneToOne
    @JoinColumn(name = "wb_id",unique=true)
    private WorkBook workBook;
}
