package com.stone.microstone.dto.workbook;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter @Getter
@Schema(description = "PDF 데이터 정보")
public class PdfDTO {
    @Schema(description = "PDF 파일 제목",example = "문제집2.pdf")
    public String fileName;
    @Schema(description = "pdf 파일 저장경로",example = "/uploads/problem_2.pdf")
    public String filePath;
}
