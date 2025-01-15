package com.stone.microstone.service.chatgpt;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.stone.microstone.domain.entitiy.Question;
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
import com.stone.microstone.service.awss3.AwsS3Service;
import com.stone.microstone.service.workbook.WorkBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonParser;


import java.io.IOException;
import java.util.*;

@Slf4j
@Service
public class ChatGPTServiceImpl implements ChatGPTService {

    private final ChatGPTConfig chatGPTConfig;
    private final WorkBookService workBookService;
    private final WorkBookRepository workBookRepository;
    private final AwsS3Service awsS3Service;

    public ChatGPTServiceImpl(ChatGPTConfig chatGPTConfig,
                              WorkBookService workBookService,
                              WorkBookRepository workBookRepository,
                              AwsS3Service awsS3Service) {
        this.chatGPTConfig = chatGPTConfig;
        this.workBookService = workBookService;
        this.workBookRepository = workBookRepository;
        this.awsS3Service = awsS3Service;
    }

    @Value("${app.test-mode}")
    private boolean testMode;

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

            om.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
            om.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
            om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // 응답 본문을 로그로 출력하여 디버깅
            log.debug("API 응답 본문: {}", response.getBody());


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
            log.debug("JsonProcessingException :: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.debug("RuntimeException :: " + e.getMessage(), e);
        }
        return resultMap;
    }


    @Override
    public QuestionAnswerResponse processText(String problemText,String language) throws IOException {

        problemText = cleanInputText(problemText); //특수문자 정제 메소드
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
        List<Question> q=awsS3Service.uploadfile(imageQuestions, testMode);

        return workBookService.getWorkBook(textQuestions, summarizedText, answerText, imageQuestions,q);
    }

    @Override
    public Map<String, Object> summarizeText(String text) { //주어진 텍스트를 요약하는 메소드
        log.debug("[+] 문제 텍스트를 요약합니다.");
        // GPT모델에 요청을 보냄
        ChatCompletionDto chatCompletionDto = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content("Summarize the following text into concise key points for each paragraph in Korean." +
                                " Ensure the summary complies with image-generation policies and avoids sensitive or policy-violating content: " + text)
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
            String questionPrompt = "Using the summarized text, create one 4-option multiple-choice question without an introduction. Use a formal tone akin to Korean college entrance exam questions." +
                    " Label the choices as ①, ②, ③, and ④, but do not include the correct answer. If '*' is needed, use it sparingly and avoid emphasizing content with it. " + summarizedText + "Create a minimalist, flat design. Avoid any modern technology or futuristic elements in the image. Please generate an image without any language included in it";

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


    private String cleanInputText(String input) {
        // 줄바꿈 문자를 \\n으로 대체
        input = input.replace("\n", "\\n");
        // 탭 문자를 \\t로 대체
        input = input.replace("\t", "\\t");
        // 캐리지 리턴 문자를 \\r로 대체
        input = input.replace("\r", "\\r");
        // 따옴표 이스케이프 처리
        input = input.replace("\"", "\\\"");
        return input;
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
                        .content("Using the summarized text, generate 10 multiple-choice questions numbered 6 through 15. Exclude any introductory text. Use a formal tone in line with Korean college entrance exam style." +
                                " Label the options as ①, ②, ③, and ④, ensuring no answers are provided. If '*' is necessary, use it minimally and not for emphasis. " + summarizedText )
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
                        .content("Provide the exact answers to the generated questions (1–15) along with detailed explanations limited to four lines each. If a special character is needed, use it sparingly and avoid using it for emphasis. Do not include any special characters such as asterisks (*) in the answers or explanations " + combinedQuestions)
                        .build()))
                .build();
        log.debug("답변 생성 정보={}", chatCompletionDto.toString());

        List<Question> q=awsS3Service.uploadfile(imageQuestions, testMode);

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



    @Transactional
    @Override
    public QuestionAnswerResponse getRetextWorkBook() {
        Optional<WorkBook> optionalLastWorkBook = workBookRepository.findLastWorkBook();
        WorkBook lastWorkBook = optionalLastWorkBook.orElseThrow(() -> new RuntimeException("기존 문제집이 존재하지 않습니다."));

        String summarizedText = lastWorkBook.getWb_sumtext();
        String contextText = lastWorkBook.getWb_content();

        if ((summarizedText == null || summarizedText.trim().isEmpty()) &&
                (contextText == null || contextText.trim().isEmpty())) {
            throw new IllegalArgumentException("문제를 재생성할 데이터가 부족합니다.");
        }

        Map<String, Object> questionResult = regenerateQuestion(summarizedText, contextText);
        String newQuestion = (String) questionResult.get("content");
        List<Map<String, String>> imageQuestions = (List<Map<String, String>>) questionResult.get("imageQuestions");
        String textQuestions = (String) questionResult.get("textQuestions");

        Map<String, Object> answerResult = generateAnswer(imageQuestions, textQuestions);
        String answerText = (String) answerResult.get("content");

        WorkBook savedWorkBook = workBookService.findLastWorkBook(newQuestion, answerText, imageQuestions, textQuestions, testMode);

        return new QuestionAnswerResponse(
                savedWorkBook.getWb_id(),
                savedWorkBook.getWb_title(),
                newQuestion,
                answerText,
                imageQuestions,
                textQuestions
        );
    }

    @Override
    public Map<String, Object> regenerateQuestion(String summarizedText, String contextText) {
        // 생성된 문제들을 기반으로 새로운 문제를 생성하는 메소드
        ChatCompletionDto chatCompletionDto = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content("Using the provided text, create 15 new multiple-choice questions that do not repeat or overlap with the previous ones. Maintain a formal tone consistent with Korean college entrance exam questions. Label the options as ①, ②, ③, and ④, and do not include the correct answers. " +
                                "[Previous Questions] " + contextText + "[Summarized Text]" + summarizedText)
                        .build()))
                .build();
        log.info("재생성 문제 정보={}", chatCompletionDto.toString());

        try {
            return executePrompt(chatCompletionDto);
        } catch (Exception e) {
            log.error("Prompt 실행 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("문제 생성 실패", e);
        }
    }


    @Override
    public QuestionAnswerResponse reCategoryWorkBook(String category, String language) throws IOException {

        String prompt = String.valueOf(generateCategoryQuestions(category, language));
        Map<String, Object> questionResult = regenerateCategoryQuestion(prompt);

        String newQuestion = (String) questionResult.get("content");
        List<Map<String, String>> imageQuestions = (List<Map<String, String>>) questionResult.get("imageQuestions");
        String textQuestions = (String) questionResult.get("textQuestions");

        Map<String, Object> answerResult = generateAnswer(imageQuestions, textQuestions);
        String answerText = (String) answerResult.get("content");

        WorkBook savedWorkBook = workBookService.findLastWorkBook(newQuestion, answerText, imageQuestions, textQuestions, testMode);

        return new QuestionAnswerResponse(
                savedWorkBook.getWb_id(),
                savedWorkBook.getWb_title(),
                newQuestion,
                answerText,
                imageQuestions,
                textQuestions
        );
    }

    private Map<String, Object> regenerateCategoryQuestion(String prompt) {
        ChatCompletionDto questionCompletion = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content(prompt)
                        .build()))
                .build();

        try {
            // 생성된 질문 응답 받기
            return executePrompt(questionCompletion);
        } catch (Exception e) {
            log.error("카테고리 문제 생성 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("카테고리 문제 생성 실패", e);
        }
    }



    @Override
    public QuestionAnswerResponse generateCategoryQuestions(String category,String language)throws IOException {
        log.debug("카테고리 문제 생성 시작: " + category);

        String prompt;
        switch (category.toLowerCase()) {
            case "conversation":
                prompt = "Create multiple-choice questions related to common Korean conversations in daily life. Ensure the questions reflect realistic scenarios such as greetings, ordering food, asking for directions, or small talk. ";
                break;
            case "object":
                prompt = "Generate multiple-choice questions about popular Korean objects or artifacts that foreign learners might encounter in everyday life. Focus on both traditional items such as hanbok, Korean ceramics, and cultural symbols, as well as common items found in daily life like clothes, beds, computers, desks, and other household objects. Ensure the questions are relevant to typical experiences and accessible to learners.";
                break;
            case "food":
                prompt = "Create multiple-choice quiz questions about Korean food culture, including common dishes, eating etiquette, and regional specialties. Ensure the questions are based on typical experiences, such as dining at a Korean restaurant or cooking traditional dishes. ";
                break;
            case "culture":
                prompt = "Generate multiple-choice questions about Korean culture, including popular traditions, festivals, and modern practices that are commonly experienced in everyday life. The questions should focus on the most accessible cultural elements like Chuseok, Lunar New Year, or Korean pop culture.";
                break;
            default:
                throw new IllegalArgumentException("Invalid category: " + category);
        }
        // 이미지 문제 생성
        List<Map<String, String>> imageQuestions = generateImageQuestionsByCategory(prompt);

        // 텍스트 문제 생성
        String textQuestions = generateTextQuestionsByCategory(prompt);

        // QuestionAnswerResponse 객체 반환
        QuestionAnswerResponse response = new QuestionAnswerResponse();
        response.setImageQuestions(imageQuestions);
        response.setTextQuestions(textQuestions);

        Map<String, Object> answerResult = generateAnswer(imageQuestions, textQuestions);
        String answerText = (String) answerResult.get("content");

        List<Question> q=awsS3Service.uploadfile(imageQuestions, testMode);

        return workBookService.getWorkBookwithnosum(textQuestions,answerText,imageQuestions,q);
    }


    private List<Map<String, String>> generateImageQuestionsByCategory(String categoryPrompt) {
        List<Map<String, String>> imageQuestions = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String questionPrompt =
                    "Please create the multiple-choice questions and answers in Korean. Using the summarized text, create one 4-option multiple-choice question without an introduction. Use a formal tone akin to Korean college entrance exam questions." +
                    "Label the choices as ①, ②, ③, and ④, but do not include the correct answer. If '*' is needed, use it sparingly and avoid emphasizing content with it." + categoryPrompt + "Create a minimalist, flat design. Avoid any modern technology or futuristic elements in the image. Please generate an image without any language included in it";

            ChatCompletionDto questionCompletion = ChatCompletionDto.builder()
                    .model("gpt-4o-mini")
                    .messages(List.of(ChatRequestMsgDto.builder()
                            .role("user")
                            .content(questionPrompt)
                            .build()))
                    .build();

            Map<String, Object> questionResponse = executePrompt(questionCompletion);
            String questionText = (String) questionResponse.get("content");

            questionText = cleanQuestionText(questionText, i);

            String imageUrl = generateImage(questionText);

            Map<String, String> questionWithImage = new HashMap<>();
            questionWithImage.put("question", questionText);
            questionWithImage.put("imageUrl", imageUrl);

            imageQuestions.add(questionWithImage);
        }
        return imageQuestions;
    }

    private String generateTextQuestionsByCategory(String categoryPrompt) {
        String questionPrompt =
                "Please create the multiple-choice questions and answers in Korean. Using the summarized text, generate 10 multiple-choice questions numbered 6 through 15. Exclude any introductory text. Use a formal tone in line with Korean college entrance exam style." +
                " Label the options as ①, ②, ③, and ④, ensuring no answers are provided. If '*' is necessary, use it minimally and not for emphasis. " + categoryPrompt;

        ChatCompletionDto textCompletion = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content(questionPrompt)
                        .build()))
                .build();

        Map<String, Object> textQuestionsResponse = executePrompt(textCompletion);
        return (String) textQuestionsResponse.get("content");
    }
}