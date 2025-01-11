package com.stone.microstone.dto.chatgpt;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class QuestionAnswerResponse {
    private int wb_id;
    private String wb_title;
    private String question;
    private String answer;
    private List<Map<String, String>> imageQuestions;
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

