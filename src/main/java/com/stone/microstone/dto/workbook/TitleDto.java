package com.stone.microstone.dto.workbook;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor
@Schema(description = "제목 반환")
public class TitleDto {
    @Schema(description = "성공여부",example = "저장 성공")
    private String message;
    @Schema(description = "문제집 db저장된 번호",example = "2")
    private Integer wb_id;
    @Schema(description = "바뀐 제목",example = "새로운제목")
    private String wb_title;
}
