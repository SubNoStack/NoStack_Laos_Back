package com.stone.microstone.domain.entitiy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

//문제집 pdf 테이블

@Entity
@Getter
@Setter
@Table(name = "workbookpdf")
public class WorkBookPDF {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int pdf_id;

    @Column
    private String pdf_path;

    @Column
    private String file_name;

    @OneToOne
    @JoinColumn(name = "wb_id",unique=true)
    private WorkBook workBook;


}
