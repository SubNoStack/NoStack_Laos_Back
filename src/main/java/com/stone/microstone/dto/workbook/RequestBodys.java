package com.stone.microstone.dto.workbook;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@Schema(description = "사용자가 보낸 문제생성 텍스트 처리위한 요청")
public class RequestBodys {
    @Schema(description = "문제텍스트",example = "문제 생성위한 텍스트")
    @NotBlank(message="텍스트를 채워주세요")
    private String problemText;
}
