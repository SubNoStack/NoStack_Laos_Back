package com.stone.microstone.service.chatgpt;

import com.stone.microstone.domain.entitiy.WorkBook;
import com.stone.microstone.config.ChatGPTConfig;
import com.stone.microstone.dto.chatgpt.ChatCompletionDto;
import com.stone.microstone.dto.chatgpt.ChatRequestMsgDto;
import com.stone.microstone.dto.chatgpt.DalleRequestDto;
import com.stone.microstone.dto.chatgpt.QuestionAnswerResponse;
import com.stone.microstone.service.ChatGPTService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stone.microstone.repository.workbook.WorkBookRepository;
import com.stone.microstone.service.workbook.WorkBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
public class ChatGPTServiceImpl implements ChatGPTService {

    private final ChatGPTConfig chatGPTConfig;
    private final WorkBookService workBookService;
    private final WorkBookRepository workBookRepository;

    public ChatGPTServiceImpl(ChatGPTConfig chatGPTConfig,
                              WorkBookService workBookService,
                              WorkBookRepository workBookRepository) {
        this.chatGPTConfig = chatGPTConfig;
        this.workBookService = workBookService;
        this.workBookRepository = workBookRepository;
    }

    @Value("${app.test-mode}")
    private boolean testMode;


    @Override
    public Map<String, Object> summarizeText(String text) { //주어진 텍스트를 요약하는 메소드
        log.debug("[+] 문제 텍스트를 요약합니다.");
        // GPT모델에 요청을 보냄
        ChatCompletionDto chatCompletionDto = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content("다음 텍스트를 한국어로 단락별 핵심으로 요약해주되, 이미지생성시 정책에 위배되지 않도록 요약해줘: " + text)
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
        if (summarizedText == null || summarizedText.trim().isEmpty()) {
            log.error("요약된 텍스트가 없습니다.");
            throw new IllegalArgumentException("요약된 텍스트가 없습니다.");
        }

        log.debug("[+] 요약된 텍스트를 기반으로 문제를 생성합니다.");

        List<Map<String, String>> imageQuestions = generateImageQuestions(summarizedText);
        String textQuestions = generateTextQuestions(summarizedText);

        Map<String, Object> result = new HashMap<>();
        result.put("imageQuestions", imageQuestions);
        result.put("textQuestions", textQuestions);

        // 답변 생성 메서드 호출
        Map<String, Object> answerResult = generateAnswer(imageQuestions, textQuestions);
        result.put("answers", answerResult); // 답변을 결과 맵에 추가

        return result;
    }

    private List<Map<String, String>> generateImageQuestions(String summarizedText) {
        List<Map<String, String>> imageQuestions = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String questionPrompt = "다음 요약된 텍스트를 기반으로 서론없이 4지선다 객관식 문제 1개만 생성하세요. 문제 번호는 " + i + "번입니다." +
                    " 문제의 4지선다 보기 번호를 ①,②,③,④로 표시하고 수능문제처럼 말투를 사용하되, 답은 나오지 않게 생성해주세요." +
                    "'*'이 필요하면 사용하되, '*'을 사용해서 강조하지 말아줘. " + summarizedText;

            ChatCompletionDto questionCompletion = ChatCompletionDto.builder()
                    .model("gpt-4o-mini")
                    .messages(List.of(ChatRequestMsgDto.builder()
                            .role("user")
                            .content(questionPrompt)
                            .build()))
                    .build();
            Map<String, Object> questionResponse = executePrompt(questionCompletion);
            String questionText = (String) questionResponse.get("content");

            // 생성된 문제 텍스트를 정제하는 로직 추가
            questionText = cleanQuestionText(questionText, i);

            String imageUrl = generateImage(questionText);

            Map<String, String> questionWithImage = new HashMap<>();
            questionWithImage.put("question", questionText);
            questionWithImage.put("imageUrl", imageUrl);

            imageQuestions.add(questionWithImage);
        }
        return imageQuestions;
    }

    private String cleanQuestionText(String questionText, int questionNumber) {
        // 문제 번호로 시작하는지 확인하고, 그렇지 않으면 추가
        if (!questionText.trim().startsWith(questionNumber + ".")) {
            questionText = questionNumber + ". " + questionText.trim();
        }

        // 여러 문제가 포함된 경우 첫 번째 문제만 추출
        int nextQuestionIndex = questionText.indexOf((questionNumber + 1) + ".");
        if (nextQuestionIndex != -1) {
            questionText = questionText.substring(0, nextQuestionIndex).trim();
        }

        return questionText;
    }


    private String generateTextQuestions(String summarizedText) {
        ChatCompletionDto textCompletion = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content("다음 요약된 텍스트를 기반으로 서론없이 4지선다 객관식을 6번부터 15번까지 10문제 생성해줘. 문제의 4지선다 보기 번호를 ①,②,③,④로 표시하고 수능문제처럼 말투를 사용하되, 답은 나오지 않게 생성해줘. " +
                                "'*'이 필요하면 사용하되, '*'을 사용해서 강조하지 말아줘.: " + summarizedText)
                        .build()))
                .build();
        log.debug("문제 생성 정보={}", textCompletion.toString());

        Map<String, Object> textQuestionsResponse = executePrompt(textCompletion);
        return (String) textQuestionsResponse.get("content");
    }

    private String generateImage(String description) {
        if (testMode) {
            log.debug("[테스트 모드] 더미 이미지 URL 반환");
            return "https://example.com/dummy-image.jpg";
        }

        log.debug("[+] 이미지 생성 요청: {}", description);

        // OpenAI API 요청 생성
        DalleRequestDto dalleRequest = DalleRequestDto.builder()
                .model("dall-e-3")
                .prompt(description)
                .build();

        Map<String, Object> imageResponse = executePrompt(dalleRequest);

        // 이미지 URL 추출
        String imageUrl = (String) imageResponse.get("content");
        log.debug("생성된 이미지 URL: {}", imageUrl);

        return imageUrl;
    }

    @Override
    public Map<String, Object> generateAnswer(List<Map<String, String>> imageQuestions, String textQuestions) {
        if ((imageQuestions == null || imageQuestions.isEmpty()) && (textQuestions == null || textQuestions.trim().isEmpty())) {
            log.error("질문 텍스트가 없습니다.");
            throw new IllegalArgumentException("질문 텍스트가 없습니다.");
        }

        log.debug("[+] 질문 텍스트를 기반으로 답변을 생성합니다.");

        // 이미지 질문을 문자열로 변환
        String imageQuestionsString = convertImageQuestionsToString(imageQuestions);

        String combinedQuestions = "이미지 문제 (1-5번):\n" + imageQuestionsString + "\n\n텍스트 문제 (6-15번):\n" + textQuestions;

        ChatCompletionDto chatCompletionDto = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content("다음 생성된 문제들의 서론 없이 1번부터 15번까지 순서대로 정확한 답과 4줄이 넘지 않는 자세한 해설을 생성해줘. '*'이 필요하면 사용하되, '*'을 사용해서 강조하지 말아줘.: " + combinedQuestions)
                        .build()))
                .build();
        log.debug("답변 생성 정보={}", chatCompletionDto.toString());

        return executePrompt(chatCompletionDto);
    }


    // 이미지 질문을 문자열로 변환하는 메서드
    private String convertImageQuestionsToString(List<Map<String, String>> imageQuestions) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < imageQuestions.size(); i++) {
            Map<String, String> question = imageQuestions.get(i);
            sb.append((i + 1)).append(". ").append(question.get("question")).append("\n");
            sb.append("이미지 URL: ").append(question.get("imageUrl")).append("\n");
        }
        return sb.toString();
    }


    @Override
    public Map<String, Object> regenerateQuestion(String summarizedText, String contextText) {
        // 생성된 문제들을 기반으로 새로운 문제를 생성하는 메소드
        ChatCompletionDto chatCompletionDto = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content("다음 주어진 텍스트를 기반으로, 이전 문제와 겹치지 않는 새로운 객관식 문제를 서론 없이 15문제 생성해줘. 수능문제처럼 말투를 사용하되, 답은 나오지 않게 생성해줘. " +
                                "[이전문제] " + contextText + "[요약텍스트]" + summarizedText)
                        .build()))
                .build();
        log.debug("재생성 문제 정보={}", chatCompletionDto.toString());
        return executePrompt(chatCompletionDto);
    }

    private Map<String, Object> executePrompt(Object requestDto) {
        Map<String, Object> resultMap = new HashMap<>();
        HttpHeaders headers = chatGPTConfig.httpHeaders();
        HttpEntity<?> requestEntity = new HttpEntity<>(requestDto, headers);

        String promptUrl;
        String model;

        if (requestDto instanceof DalleRequestDto) {
            promptUrl = chatGPTConfig.getDalleApiUrl();
            model = ((DalleRequestDto) requestDto).getModel();
        } else if (requestDto instanceof ChatCompletionDto) {
            promptUrl = chatGPTConfig.getApiUrl();
            model = ((ChatCompletionDto) requestDto).getModel();
        } else {
            throw new IllegalArgumentException("Unsupported request type");
        }

        ResponseEntity<String> response = chatGPTConfig
                .restTemplate()
                .exchange(promptUrl, HttpMethod.POST, requestEntity, String.class);
        try {
            ObjectMapper om = new ObjectMapper();
            Map<String, Object> responseMap = om.readValue(response.getBody(), new TypeReference<>() {
            });
            log.debug("API 응답: {}", responseMap);

            if (model.startsWith("dall")) {
                // DALL-E 응답 처리
                List<Map<String, Object>> data = (List<Map<String, Object>>) responseMap.get("data");
                if (data != null && !data.isEmpty()) {
                    String imageUrl = (String) data.get(0).get("url");
                    resultMap.put("content", imageUrl);
                }
            } else {
                // GPT 응답 처리
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
                    if (message != null) {
                        resultMap.put("content", message.get("content"));
                    }
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
    public QuestionAnswerResponse processText(String problemText) throws IOException {
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
        String textQuestions = (String) questionResult.get("textQuestions");
        List<Map<String, String>> imageQuestions = (List<Map<String, String>>) questionResult.get("imageQuestions");
        log.debug("생성된 질문: " + textQuestions);

        // 3단계: 질문으로 답변 생성
        Map<String, Object> answerResult = generateAnswer(imageQuestions, textQuestions);
        String answerText = (String) answerResult.get("content");
        if (answerText == null || answerText.trim().isEmpty()) {
            throw new IllegalArgumentException("생성된 답변이 없습니다.");
        }
        log.debug("생성된 답변: " + answerText);

        return workBookService.getWorkBook(textQuestions, summarizedText, answerText, imageQuestions);
    }

    @Transactional
    @Override
    public QuestionAnswerResponse getRetextWorkBook(int userId) {
        // 마지막 문제집을 가져옵니다.
        Optional<WorkBook> newwork = workBookRepository.findLastWorkBook();
        WorkBook lastWorkBook = newwork.orElseThrow(() -> new RuntimeException("기존 문제집이 존재하지 않음. User ID: " + userId));

        // 새로운 문제를 생성합니다.
        Map<String, Object> questionResult = regenerateQuestion(lastWorkBook.getWb_sumtext(), lastWorkBook.getWb_content());
        String newQuestion = (String) questionResult.get("content");
        log.debug("새 질문={}", newQuestion);

        // 이미지 질문과 텍스트 질문을 받아옵니다.
        List<Map<String, String>> imageQuestions = (List<Map<String, String>>) questionResult.get("imageQuestions");
        String textQuestions = (String) questionResult.get("textQuestions");

        // 생성된 질문에 대한 답변을 생성합니다.
        Map<String, Object> answerResult = generateAnswer(imageQuestions, textQuestions);
        String answerText = (String) answerResult.get("content");
        if (answerText == null || answerText.trim().isEmpty()) {
            throw new RuntimeException("생성된 답변이 없습니다.");
        }

        // 새로운 문제집을 저장합니다.
        WorkBook saveWorkBook = workBookService.findLastWorkBook(newQuestion, answerText, userId);

        return new QuestionAnswerResponse(saveWorkBook.getWb_id(), saveWorkBook.getWb_title(), newQuestion, answerText, imageQuestions, textQuestions);
    }
}