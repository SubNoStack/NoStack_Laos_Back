package com.stone.microstone.service;

import com.stone.microstone.dto.chatgpt.QuestionAnswerResponse;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ChatGPTService {
    Map<String, Object> summarizeText(String text, String language);
    Map<String, Object> generateQuestion(String summarizedText, String language);
    Map<String, Object> regenerateQuestion(String summarizedText, String contextText, String language);
    Map<String, Object> generateAnswer(List<Map<String, String>> imageQuestions, String textQuestions, String language);

    QuestionAnswerResponse processText(String problemText, String language, String category) throws IOException;

    @Transactional
    QuestionAnswerResponse getRetextWorkBook();

    QuestionAnswerResponse generateCategoryQuestions(String category, String language) throws IOException;

    QuestionAnswerResponse reCategoryWorkBook() throws IOException;

    Map<String, Object> regenerateCategoryQuestions(String prompt, String language);
}