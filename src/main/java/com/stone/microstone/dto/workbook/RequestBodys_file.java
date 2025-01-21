package com.stone.microstone.dto.workbook;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "사용자가 보낸 pdf파일 처리 위한 요청")
public class RequestBodys_file {
    @Schema(description = "PDF 파일", type = "pdf_file", format = "multipart/form-data")
    @NotNull(message = "파일을 선택해주세요")
    private MultipartFile file;
}
