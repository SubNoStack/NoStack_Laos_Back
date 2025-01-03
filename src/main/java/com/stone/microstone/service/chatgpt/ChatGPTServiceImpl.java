package com.stone.microstone.service.chatgpt;

import com.stone.microstone.domain.entitiy.WorkBook;
import com.stone.microstone.config.ChatGPTConfig;
import com.stone.microstone.dto.chatgpt.ChatCompletionDto;
import com.stone.microstone.dto.chatgpt.ChatRequestMsgDto;
import com.stone.microstone.dto.chatgpt.QuestionAnswerResponse;
import com.stone.microstone.service.ChatGPTService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stone.microstone.domain.entitiy.LocalUser;
import com.stone.microstone.repository.social.LocalUserRepository;
import com.stone.microstone.repository.workbook.WorkBookRepository;
import com.stone.microstone.service.workbook.WorkBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class ChatGPTServiceImpl implements ChatGPTService {

    private final ChatGPTConfig chatGPTConfig;
    private final WorkBookService workBookService;
    private final LocalUserRepository userRepository;
    private final WorkBookRepository workBookRepository;

    public ChatGPTServiceImpl(ChatGPTConfig chatGPTConfig,
                              WorkBookService workBookService,
                              LocalUserRepository userRepository,
                              WorkBookRepository workBookRepository) {
        this.chatGPTConfig = chatGPTConfig;
        this.workBookService = workBookService;
        this.userRepository = userRepository;
        this.workBookRepository = workBookRepository;
    }

    @Override
    public Map<String, Object> summarizeText(String text) { //주어진 텍스트를 요약하는 메소드
        log.debug("[+] 문제 텍스트를 요약합니다.");
        // GPT모델에 요청을 보냄
        ChatCompletionDto chatCompletionDto = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content("다음 텍스트를 한국어로 단락별 핵심으로 요약해줘: " + text)
                        .build()))
                .build();
        log.debug("요약된 정보={}", chatCompletionDto.toString());
        // GPT로부터 응답을 받아서 결과를 반환
        Map<String, Object> response = executePrompt(chatCompletionDto);
        log.debug("요약 응답: {}", response.get("content"));

        return response;
    }

    @Override
    public Map<String, Object> generateQuestion(String summarizedText) {
        //요약된 텍스트를 기반으로 문제를 생성하는 메소드
        if (summarizedText == null || summarizedText.trim().isEmpty()) {
            log.error("요약된 텍스트가 없습니다.");
            throw new IllegalArgumentException("요약된 텍스트가 없습니다.");
        }

        log.debug("[+] 요약된 텍스트를 기반으로 문제를 생성합니다.");
        //GPT에 문제 생성 요청
        ChatCompletionDto chatCompletionDto = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content("다음 요약된 텍스트를 기반으로 서론없이 객관식을 15문제 생성해줘. 수능문제처럼 말투를 사용하되, 답은 나오지 않게 생성해줘. : " + summarizedText)
                        .build()))
                .build();
        log.debug("문제 생성 정보={}", chatCompletionDto.toString());

        return executePrompt(chatCompletionDto);
    }

    @Override
    public Map<String, Object> generateAnswer(String questionText) {
        //생성된 문제들을 기반으로 답을 생성하는 메소드
        if (questionText == null || questionText.trim().isEmpty()) {
            log.error("질문 텍스트가 없습니다.");
            throw new IllegalArgumentException("질문 텍스트가 없습니다.");
        }

        log.debug("[+] 질문 텍스트를 기반으로 답변을 생성합니다.");
        //GPT에 답 생성 요청
        ChatCompletionDto chatCompletionDto = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content("다음 생성된 문제들의 서론없이 정확한 답과 4줄이 넘지 않는 자세한 해설을 생성해줘. '*'이 필요하면 사용하되, '*'을 사용해서 강조하지 말아줘.: " + questionText)
                        .build()))
                .build();
        log.debug("답변 생성 정보={}", chatCompletionDto.toString());

        return executePrompt(chatCompletionDto);
    }

    @Override
    public Map<String, Object> regenerateQuestion(String summarizedText,String contextText) {
        // 생성된 문제들을 기반으로 새로운 문제를 생성하는 메소드
        ChatCompletionDto chatCompletionDto = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content("다음 주어진 텍스트를 기반으로, 이전 문제와 겹치지 않는 새로운 객관식 문제를 서론 없이 15문제 생성해줘. 수능문제처럼 말투를 사용하되, 답은 나오지 않게 생성해줘. " +
                                "[이전문제] "+contextText + "[요약텍스트]" +summarizedText)
                        .build()))
                .build();
        log.debug("재생성 문제 정보={}", chatCompletionDto.toString());
        return executePrompt(chatCompletionDto);
    }

    private Map<String, Object> executePrompt(ChatCompletionDto chatCompletionDto) {
        //GPT 모델에 요청을 보내고 응답을 처리하는 메소드
        Map<String, Object> resultMap = new HashMap<>();
        HttpHeaders headers = chatGPTConfig.httpHeaders();
        HttpEntity<ChatCompletionDto> requestEntity = new HttpEntity<>(chatCompletionDto, headers);

        String promptUrl = chatGPTConfig.getApiUrl(); // 설정된 API URL 가져오기
        //API요청을 보내고 응답을 처리
        ResponseEntity<String> response = chatGPTConfig
                .restTemplate()
                .exchange(promptUrl, HttpMethod.POST, requestEntity, String.class);
        try {
            ObjectMapper om = new ObjectMapper();
            Map<String, Object> responseMap = om.readValue(response.getBody(), new TypeReference<>() {});
            log.debug("API 응답: {}", responseMap); // 응답 확인을 위한 로그

            // 응답에서 필요한 부분을 추출
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> firstChoice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
                if (message != null) {
                    resultMap.put("content", message.get("content"));
                }
            }
        } catch (JsonProcessingException e) {
            log.debug("JsonProcessingException :: " + e.getMessage());
        } catch (RuntimeException e) {
            log.debug("RuntimeException :: " + e.getMessage());
        }
        return resultMap;
    }

    @Override
    public QuestionAnswerResponse processText(String problemText, Integer userId) throws IOException {
        log.debug("받은 문제 텍스트: " + problemText);
        // 문제 텍스트를 처리하여 요약, 문제생성, 답변 생성 3단계를 수행
        // 1단계: 문제 텍스트 요약
        Map<String, Object> summaryResult = summarizeText(problemText);
        String summarizedText = (String) summaryResult.get("content");
        if (summarizedText == null || summarizedText.trim().isEmpty()) {
            throw new IllegalArgumentException("요약된 텍스트가 없습니다.");
        }
        log.debug("요약된 텍스트: " + summarizedText);

        // 2단계: 요약된 텍스트로 문제 생성
        Map<String, Object> questionResult = generateQuestion(summarizedText);
        String question = (String) questionResult.get("content");
        log.debug("생성된 질문: " + question);

        // 3단계: 질문으로 답변 생성
        Map<String, Object> answerResult = generateAnswer(question);
        String answerText = (String) answerResult.get("content");
        if (answerText == null || answerText.trim().isEmpty()) {
            throw new IllegalArgumentException("생성된 답변이 없습니다.");
        }
        log.debug("생성된 답변: " + answerText);

        return workBookService.getWorkBook(question, summarizedText, answerText, userId);
    }

    @Transactional
    @Override
    public QuestionAnswerResponse getRetextWorkBook(int userId){
        // 기존 문제집을 기반으로 새 문제집을 생성하는 메소드
        Optional<LocalUser> userOptional = userRepository.findById(userId);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + userId));

        Optional<WorkBook> newwork = workBookRepository.findLastWorkBook(user);
        //재생성을위해 기존에 저장된 요약문을 가져옴
        WorkBook lastWorkBook=newwork.orElseThrow(() -> new RuntimeException("기존 문제집이 존재하지 않음. User ID: " + userId));
        // 저장된 요약 텍스트로 새로운 문제를 생성
        Map<String,Object> questionResult = regenerateQuestion(lastWorkBook.getWb_sumtext(), lastWorkBook.getWb_content()); //저장해둔 요약문자로 다시 생성.
        String newQuestion=(String) questionResult.get("content");
        log.debug("새 질문={}",newQuestion);

        //답 생성.
        Map<String, Object> answerResult = generateAnswer(newQuestion);
        String answerText = (String) answerResult.get("content");
        if (answerText == null || answerText.trim().isEmpty()) {
            throw new RuntimeException("생성된 답변이 없습니다.");
        }
        //저장 작업 수행.
        WorkBook saveWorkBook = workBookService.findLastWorkBook(newQuestion,answerText, userId);

        return new QuestionAnswerResponse(saveWorkBook.getWb_user_id(),saveWorkBook.getWb_title(),newQuestion,answerText);


    }


}



