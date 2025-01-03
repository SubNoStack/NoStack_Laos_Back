package com.stone.microstone.dto.workbook;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter @Setter
public class WorkBookAnswerResponse {
    private Integer wb_id;
    private String wb_title;
    private LocalDate wb_create;
    private String wb_answer;
    private boolean favorite;
    private PdfDTO workbook_pdf;
}
