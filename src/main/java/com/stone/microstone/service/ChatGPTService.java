package com.stone.microstone.service;

import com.stone.microstone.dto.chatgpt.QuestionAnswerResponse;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Map;

public interface ChatGPTService {
    Map<String, Object> summarizeText(String text);
    Map<String, Object> generateQuestion(String summarizedText);
    Map<String,Object> regenerateQuestion(String summarizedText,String contextText);
    Map<String, Object> generateAnswer(String questionText);

    QuestionAnswerResponse processText(String problemText, Integer userId) throws IOException;

    @Transactional
    QuestionAnswerResponse getRetextWorkBook(int userId);

}