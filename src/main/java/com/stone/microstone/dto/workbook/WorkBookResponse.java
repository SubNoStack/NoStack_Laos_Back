package com.stone.microstone.dto.workbook;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter @Setter
@Schema(description = "문제집 응답 데이터")
public class WorkBookResponse {
    @Schema(description = "문제집 db저장 id(이걸로 조회)",example = "2")
    private Integer wb_id;
    @Schema(description = "문제집 제목",example = "문제집2")
    private String wb_title;
    @Schema(description = "문제집 생성 시간",example="2025-01-14")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate wb_create;
    @Schema(description = "문제집 전체 내용.",example = "문제집 내용")
    private String wb_content;
    @Schema(description ="pdf 제목,저장경로 담긴것" )
    private PdfDTO workbook_pdf;

}
