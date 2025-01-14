package com.stone.microstone.dto.workbook;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@Schema(description = "오류응답")
public class ErrorResponse {
    @Schema(description = "오류 메시지",example="잘못된 요청.")
    private String error;
}
