package com.stone.microstone.dto.chatgpt;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class QuestionAnswerResponse {
    @Schema(description = "저장된 번호",example = "1")
    private int wb_id;
    @Schema(description = "문제집 제목",example="제목")
    private String wb_title;
    @Schema(description = "문제 전체 내용",example="문제집 내용")
    private String question;
    @Schema(description = "문제대한 답지",example="답지 내용")
    private String answer;
    @ArraySchema(
            schema = @Schema(
                    description = "이미지 문제와 이미지url",
                    example = "{\"question\":\"1번~5번문제내용\",\"imageUrl\":\"url내용\"}"
            ),
            arraySchema = @Schema(description = "5개의 이미지 문제내용과 url 리스트들")
    )
    private List<Map<String, String>> imageQuestions;
    @Schema(description = "나머지 6번~15번까지 전달",example="6번~15번문제 전체")
    private String textQuestions;

    public QuestionAnswerResponse(int wb_id, String wb_title, String question, String answer, List<Map<String, String>> imageQuestions, String textQuestions) {
        this.wb_id = wb_id;
        this.wb_title = wb_title;
        this.question = question;
        this.answer = answer;
        this.imageQuestions = imageQuestions;
        this.textQuestions = textQuestions;
    }
}

