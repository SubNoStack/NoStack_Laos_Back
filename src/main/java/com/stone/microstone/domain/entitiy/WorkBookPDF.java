package com.stone.microstone.domain.entitiy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

//문제집 pdf 테이블

@Entity
@Getter
@Setter
public class WorkBookPDF {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int pdf_id;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] pdf_data;

    private String fileName;

    @Column
    private String pdf_path;

    @OneToOne
    @JoinColumn(name="wb_id",unique = true)
    private WorkBook workBook;

}
