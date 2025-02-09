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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;


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
    private boolean testMode; // 테스트 모드 활성화 여부

    // GPT, DALL-E 요청을 처리하는 메소드
    private Map<String, Object> executePrompt(Object requestDto) {
        Map<String, Object> resultMap = new HashMap<>();
        HttpHeaders headers = chatGPTConfig.httpHeaders();
        HttpEntity<?> requestEntity = new HttpEntity<>(requestDto, headers);

        String promptUrl;
        String model;

        // 요청 유형에 따라 URL 및 모델 선택
        if (requestDto instanceof DalleRequestDto) {
            promptUrl = chatGPTConfig.getDalleApiUrl(); // DALL-E API URL 설정
            model = ((DalleRequestDto) requestDto).getModel();
        } else if (requestDto instanceof ChatCompletionDto) {
            promptUrl = chatGPTConfig.getApiUrl(); // ChatGPT API URL 설정
            model = ((ChatCompletionDto) requestDto).getModel();
        } else {
            throw new IllegalArgumentException("Unsupported request type"); // 지원되지 않는 요청 유형 예외 처리
        }

        // OpenAI API 호출 및 응답 처리
        ResponseEntity<String> response = chatGPTConfig
                .restTemplate()
                .exchange(promptUrl, HttpMethod.POST, requestEntity, String.class);
        try {
            ObjectMapper om = new ObjectMapper();
            // JSON 파싱 옵션 설정
            om.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
            om.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
            om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            // 응답 본문을 로그로 출력하여 디버깅
            log.debug("API 응답 본문: {}", response.getBody());


            Map<String, Object> responseMap = om.readValue(response.getBody(), new TypeReference<>() {
            });
            log.debug("API 응답: {}", responseMap);

            if (model.startsWith("dall")) {
                // DALL-E 응답 처리 (이미지 URL 추출)
                List<Map<String, Object>> data = (List<Map<String, Object>>) responseMap.get("data");
                if (data != null && !data.isEmpty()) {
                    String imageUrl = (String) data.get(0).get("url");
                    resultMap.put("content", imageUrl);
                }
            } else {
                // GPT 응답 처리 (텍스트 응답 추출)
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
    //입력된 텍스트에서 특수 문자를 이스케이프 처리하여 API 요청 시 안정성을 보장하는 메서드
    private String cleanInputText(String input) {
        input = input.replace("\n", "\\n"); // 줄바꿈 문자를 \\n으로 대체
        input = input.replace("\t", "\\t"); // 탭 문자를 \\t로 대체
        input = input.replace("\r", "\\r"); // 캐리지 리턴 문자를 \\r로 대체
        input = input.replace("\"", "\\\""); // 따옴표 이스케이프 처리
        return input;
    }

    /**
     * @param problemText 문제 원본 텍스트
     * @param language 문제 생성 언어 (예: korea, english, Lao language)
     * @param category 문제 카테고리 (예: objcet, food)
     * @return 생성된 문제 및 답변을 포함한 QuestionAnswerResponse 객체
     */
    @Override  //주어진 문제 텍스트를 요약하고 문제를 생성하는 프로세스를 수행하는 메서드
    public QuestionAnswerResponse processText(String problemText, String language, String category) throws IOException {
        problemText = cleanInputText(problemText);
        log.debug("받은 문제 텍스트: " + problemText);

        // 1. 문제 텍스트 요약
        Map<String, Object> summaryResult = summarizeText(problemText, language);
        String summarizedText = (String) summaryResult.get("content");
        if (summarizedText == null || summarizedText.trim().isEmpty()) {
            throw new IllegalArgumentException("요약된 텍스트가 없습니다.");
        }
        log.debug("요약된 텍스트: " + summarizedText);

        // 2. 15개의 문제 생성
        Map<String, Object> questionResult = generateQuestion(summarizedText, language);
        List<String> allQuestions = (List<String>) questionResult.get("questions");
        if (allQuestions.size() < 15) {
            throw new IllegalArgumentException("15개의 문제가 생성되지 않았습니다.");
        }

        // 3. 이미지 문제를 병렬로 생성 (1~5번 문제만 이미지로 변환)
        List<String> textQuestions = allQuestions.subList(5, 15);
        List<String> imageQuestionsTexts = allQuestions.subList(0, 5);
        List<Map<String, String>> imageQuestions = generateImageQuestions(imageQuestionsTexts, language);

        log.debug("텍스트 문제: " + textQuestions);
        log.debug("이미지 문제: " + imageQuestions);

        // 4. 질문으로 답변 생성
        Map<String, Object> answerResult = generateAnswer(imageQuestions, String.join("\n", textQuestions), language);
        String answerText = (String) answerResult.get("content");
        if (answerText == null || answerText.trim().isEmpty()) {
            throw new IllegalArgumentException("생성된 답변이 없습니다.");
        }
        log.debug("생성된 답변: " + answerText);

        // 5. 이미지 문제를 S3에 업로드 후 최종 Workbook 반환
        List<Question> q = awsS3Service.uploadfile(imageQuestions, testMode);
        return workBookService.getWorkBook(textQuestions.toString(), summarizedText, answerText, imageQuestions, q, language, category);
    }

    /**
     * 주어진 텍스트를 GPT 모델을 이용해 요약하는 메서드
     * @param text 원본 텍스트
     * @param language 요약할 언어
     * @return 요약된 텍스트 결과 (Map 형태)
     */
    @Override
    public Map<String, Object> summarizeText(String text, String language) { //주어진 텍스트를 요약하는 메소드
        log.debug("[+] 문제 텍스트를 요약합니다.");
        // GPT모델에 요청을 보냄
        ChatCompletionDto chatCompletionDto = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content("Summarize the following text into concise key points for each paragraph in " + language +
                                ". Ensure the summary complies with image-generation policies and avoids sensitive or policy-violating content: " + text
                        )
                        .build()))
                .build();
        log.debug("요약된 정보={}", chatCompletionDto.toString());
        // GPT로부터 응답을 받아서 결과를 반환
        Map<String, Object> response = executePrompt(chatCompletionDto);
        log.debug("요약 응답: {}", response.get("content"));

        return response;
    }

    /**
     * 요약된 텍스트를 바탕으로 15개의 객관식 문제를 생성하는 메서드
     * @param summarizedText 요약된 문제 텍스트
     * @param language 문제 생성 언어
     * @return 15개의 문제 리스트
     */
    @Override
    public Map<String, Object> generateQuestion(String summarizedText, String language) {
        log.debug("[+] 요약된 텍스트를 기반으로 문제를 생성합니다. 언어: {}", language);

        // 15개의 문제를 한 번에 생성
        List<String> allQuestions = generateTextQuestions(summarizedText, language);
        if (allQuestions.size() < 15) {
            throw new IllegalArgumentException("15개의 문제가 생성되지 않았습니다.");
        }

        // 결과 맵에 저장
        Map<String, Object> result = new HashMap<>();
        result.put("questions", allQuestions);

        return result;
    }

    /**
     * 이미지 기반 문제를 생성하는 메서드
     * @param imageQuestionsTexts 이미지 문제로 변환할 질문 목록
     * @param language 문제 생성에 사용할 언어
     * @return 생성된 이미지 문제 목록 (각 문제는 'question'과 'imageUrl'을 포함하는 맵 형태)
     */
    private List<Map<String, String>> generateImageQuestions(List<String> imageQuestionsTexts, String language) {
        ExecutorService executorService = Executors.newFixedThreadPool(5);

        List<CompletableFuture<Map<String, String>>> futures = new ArrayList<>();

        for (String questionText : imageQuestionsTexts) {
            CompletableFuture<Map<String, String>> future = CompletableFuture.supplyAsync(() -> {
                String imageUrl = generateImage(questionText); // 질문에 대한 이미지 생성

                Map<String, String> questionWithImage = new HashMap<>();
                questionWithImage.put("question", questionText);
                questionWithImage.put("imageUrl", imageUrl);

                return questionWithImage;
            }, executorService);
            futures.add(future);
        }

        // 모든 작업 완료까지 대기 후 결과 반환
        List<Map<String, String>> imageQuestions = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        executorService.shutdown(); // 스레드 풀 종료
        return imageQuestions;
    }

    /**
     * 텍스트 기반 문제를 생성하는 메서드
     * @param summarizedText 요약된 텍스트 (문제 출제의 기반이 되는 내용)
     * @param language 문제 생성에 사용할 언어
     * @return 생성된 텍스트 문제 목록 (각 문제는 리스트의 개별 요소로 반환)
     */
    private List<String> generateTextQuestions(String summarizedText, String language) {
        ChatCompletionDto textCompletion = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content("Using the summarized text, generate 15 multiple-choice questions numbered 1 through 15.\n\n" +
                                "Ensure that each question follows this format:\n" +
                                "(1) The question statement, followed by a newline.\n" +
                                "(2) Four answer choices labeled as ①, ②, ③, and ④, each on a new line.\n" +
                                "Do not include any introductory or explanatory text.\n\n" +
                                "Use a formal tone in line with Korean college entrance exam style.\n" +
                                "Create the problems using " + language + ".\n\n" +
                                "Here is the summarized text:\n" + summarizedText)
                        .build()))
                .build();

        log.debug("문제 생성 정보={}", textCompletion.toString());

        Map<String, Object> textQuestionsResponse = executePrompt(textCompletion);
        String questionsText = (String) textQuestionsResponse.get("content");

        // 문제 번호(1. ~ 15.)를 기준으로 문제를 나누기
        List<String> splitQuestions = Arrays.asList(questionsText.split("(?=\\b\\d{1,2}\\.)"));

        return splitQuestions;
    }

    /**
     * 이미지 생성 요청을 처리하는 메서드
     * @param description 이미지 문제로 변환할 질문 설명
     * @return 생성된 이미지의 URL
     */
    private String generateImage(String description) {
        if (testMode) {
            log.debug("[테스트 모드] 더미 이미지 URL 반환");
            return "https://www.dummyimage.com/1024x1024/000/fff.jpg&text=No+Stack";
        }

        log.debug("[+] 이미지 생성 요청: {}", description);

        // DALL-E 요청 텍스트
        String prompt = description + " Create a detailed and accurate visual representation of the problem described above." +
                " Ensure the image visually represents the context of the question and the correct answer." +
                " Highlight the key elements related to the correct choice while ensuring the design remains minimalist, formal, and suitable for academic purposes." +
                " Do not include any text or language in the image, but visually emphasize the core idea of the correct answer.";

        // OpenAI API 요청 생성
        DalleRequestDto dalleRequest = DalleRequestDto.builder()
                .model("dall-e-3")
                .prompt(prompt)
                .build();

        Map<String, Object> imageResponse = executePrompt(dalleRequest);

        // 이미지 URL 추출
        String imageUrl = (String) imageResponse.get("content");
        log.debug("생성된 이미지 URL: {}", imageUrl);

        return imageUrl;
    }

    /**
     * 생성된 문제에 대한 정답을 생성하는 메서드
     * @param imageQuestions 이미지 기반 문제 목록
     * @param textQuestions 텍스트 기반 문제 목록을 문자열로 변환한 값
     * @param language 문제 및 답변을 생성할 언어
     * @return 생성된 답변 데이터를 포함하는 맵
     */
    @Override
    public Map<String, Object> generateAnswer(List<Map<String, String>> imageQuestions, String textQuestions, String language) {
        if ((imageQuestions == null || imageQuestions.isEmpty()) && (textQuestions == null || textQuestions.trim().isEmpty())) {
            log.error("질문 텍스트가 없습니다.");
            throw new IllegalArgumentException("질문 텍스트가 없습니다.");
        }

        log.debug("[+] 질문 텍스트를 기반으로 답변을 생성합니다.");

        // 이미지 질문을 문자열로 변환
        String imageQuestionsString = convertImageQuestionsToString(imageQuestions);

        String combinedQuestions = "문제 (1-5번):\n" + imageQuestionsString + "\n\n문제 (6-15번):\n" + textQuestions;

        String prompt = "Provide the exact answers to the generated questions (1–15). "
                + "Format each answer as follows:\n"
                + "1. " + getCorrectAnswer(language) + " [Actual correct answer]\n"
                + getExplanation(language) + " [Explanation must be written in " + language + "]\n\n"
                + "If a special character is needed, use it sparingly and avoid using it for emphasis. "
                + "Do not include any special characters such as asterisks (*) in the answers or explanations. "
                + "Generate answers based on the given question.\n\n"
                + combinedQuestions;

        ChatCompletionDto chatCompletionDto = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content(prompt)
                        .build()))
                .build();
        log.debug("답변 생성 정보={}", chatCompletionDto.toString());

        Map<String, Object> response = executePrompt(chatCompletionDto);

        if (response == null || response.isEmpty()) {
            throw new RuntimeException("답변 생성 실패");
        }

        return response;
    }

    private String getCorrectAnswer(String language) { // 정답을 입력받은 언어에 따라서 다르게 표시하기 위한 메소드
        switch (language.toLowerCase()) {
            case "korea":
                return "정답:";
            case "english":
                return "Correct Answer:";
            case "Lao language":
                return "ຄຳອະທິບາຍ:";
            default:
                return "Correct Answer:"; // 기본값 (영어)
        }
    }

    private String getExplanation(String language) { // 해설을 입력받은 언어에 따라서 다르게 표시하기 위한 메소드
        switch (language.toLowerCase()) {
            case "korea":
                return "해설:";
            case "english":
                return "Explanation:";
            case "Lao language":
                return "ຄໍາຕອບທີ່ຖືກຕ້ອງ:";
            default:
                return "Explanation:"; // 기본값 (영어)
        }
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
        WorkBook lastWorkBook = workBookRepository.findLastWorkBook() // 기존에 저장된 마지막 문제집을 가져옴
                .orElseThrow(() -> new RuntimeException("기존 문제집이 존재하지 않습니다."));
        // 문제를 재생성하기 위한 데이터 가져오기
        String summarizedText = lastWorkBook.getWb_sumtext(); // 요약된 텍스트
        String contextText = lastWorkBook.getWb_content(); //기존 문제집
        String language = lastWorkBook.getWb_language(); // 사용언어

        if ((summarizedText == null || summarizedText.trim().isEmpty()) && // 문제를 생성할 데이터가 부족할 경우 예외 발생
                (contextText == null || contextText.trim().isEmpty())) {
            throw new IllegalArgumentException("문제를 재생성할 데이터가 부족합니다.");
        }
        // 1. 새로운 문제로 재생성 (텍스트 + 이미지 문제 포함)
        Map<String, Object> questionResult = regenerateQuestion(summarizedText, contextText, language);
        List<String> allQuestions = (List<String>) questionResult.get("questions");
        if (allQuestions.size() < 15) {
            throw new IllegalArgumentException("15개의 문제가 생성되지 않았습니다.");
        }
        // 2. 이미지 문제: 처음 5개 텍스트 문제: 6~15번 (5~14 인덱스)
        List<String> textQuestions = allQuestions.subList(5, 15);
        List<String> imageQuestionsTexts = allQuestions.subList(0, 5);
        List<Map<String, String>> imageQuestions = generateImageQuestions(imageQuestionsTexts, language);

        log.debug("텍스트 문제: " + textQuestions);
        log.debug("이미지 문제: " + imageQuestions);

        // 3. 정답 생성
        Map<String, Object> answerResult = generateAnswer(imageQuestions, String.join("\n", textQuestions), language);
        String answerText = (String) answerResult.get("content");
        if (answerText == null || answerText.trim().isEmpty()) {
            throw new IllegalArgumentException("생성된 답변이 없습니다.");
        }
        log.debug("생성된 답변: " + answerText);

        // 4. 새롭게 생성된 문제집 저장
        WorkBook savedWorkBook = workBookService.findLastWorkBook(
                String.join("\n", textQuestions),
                answerText,
                imageQuestions,
                String.join("\n", textQuestions),
                testMode);

        return new QuestionAnswerResponse(
                savedWorkBook.getWb_id(),
                savedWorkBook.getWb_title(),
                savedWorkBook.getWb_content(),
                savedWorkBook.getWb_answer(),
                imageQuestions,
                String.join("\n", textQuestions)
        );
    }

    @Override
    public Map<String, Object> regenerateQuestion(String summarizedText, String contextText, String language) {
        if (summarizedText == null || summarizedText.trim().isEmpty()) { // 요약된 텍스트가 없을 경우 예외 발생
            log.error("요약된 텍스트가 없습니다.");
            throw new IllegalArgumentException("요약된 텍스트가 없습니다.");
        }
        log.debug("[+] 기존 문제들을 기반으로 새로운 문제를 생성합니다.");
            // 15개의 문제를 한 번에 생성
        List<String> allQuestions = regenerateTextQuestions(summarizedText, contextText);
        if (allQuestions.size() < 15) {
            throw new IllegalArgumentException("15개의 문제가 생성되지 않았습니다.");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("questions", allQuestions);

        return result;
    }

    /**
     * 기존 문제를 기반으로 15개의 새로운 텍스트 문제를 생성하는 메서드
     * @param summarizedText 요약된 텍스트
     * @param contextText 기존 문제집 텍스트
     * @return 새롭게 생성된 문제 리스트 (15개)
     */
    private List<String> regenerateTextQuestions(String summarizedText, String contextText) {
        String questionPrompt = "Using the summarized text, generate 15 new multiple-choice questions numbered 1 through 15.\n\n" +
                "Ensure that each question follows this format:\n" +
                "(1) The question statement, followed by a newline.\n" +
                "(2) Four answer choices labeled as ①, ②, ③, and ④, each on a new line.\n" +
                "Do not include any introductory or explanatory text.\n\n" +
                "Maintain a formal tone similar to Korean college entrance exams.\n\n" +
                "Ensure these questions do not overlap with the previous ones.\n" +
                "Here are the previous questions:\n" + contextText + "\n\n" +
                "Here is the summarized text:\n" + summarizedText;

        ChatCompletionDto textCompletion = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content(questionPrompt)
                        .build()))
                .build();

        log.debug("재생성 텍스트 문제 요청: {}", textCompletion.toString());
        // AI 모델을 호출하여 문제 생성
        Map<String, Object> textQuestionsResponse = executePrompt(textCompletion);
        String questionsText = (String) textQuestionsResponse.get("content");
        // 문제 번호를 기준으로 문제를 나누어 리스트로 변환
        List<String> splitQuestions = Arrays.asList(questionsText.split("(?=\\b\\d{1,2}\\.)"));
        return splitQuestions;
    }

    @Override
    public QuestionAnswerResponse generateCategoryQuestions(String category, String language) throws IOException {
        log.debug("카테고리 문제 생성 시작: " + category);

        String prompt;
        switch (category.toLowerCase()) {
            case "conversation":
                prompt = "Create multiple-choice questions related to common Korean conversations in daily life. Focus on realistic scenarios such as greetings, ordering food, asking for directions, or making small talk. For example, questions could ask which phrase is appropriate in a given situation, or what the appropriate response would be.";
                break;
            case "object":
                prompt = "Generate multiple-choice questions about Korean objects or artifacts that foreign learners might encounter in everyday life. The questions should ask for the Korean name of common objects or traditional items such as hanbok, Korean ceramics, and cultural symbols, as well as everyday items like furniture, clothes, or gadgets.";
                break;
            case "food":
                prompt = "Create multiple-choice quiz questions about Korean food culture. Include common dishes, eating etiquette, and regional specialties. Focus on everyday food items, such as popular street food or dishes found in Korean homes, not necessarily traditional ones.";
                break;
            case "culture":
                prompt = "Generate multiple-choice questions about Korean culture, including popular traditions, festivals, and modern practices. The questions should cover well-known cultural elements, such as holidays like Chuseok or Lunar New Year, as well as aspects of contemporary Korean pop culture.";
                break;
            default:
                throw new IllegalArgumentException("Invalid category: " + category);
        }
        // 1. 문제 15개 생성
        List<String> allQuestions = generateTextQuestions(prompt, language);
        if (allQuestions.size() < 15) {
            throw new IllegalArgumentException("15개의 문제가 생성되지 않았습니다.");
        }

        // 2. 문제를 분리 (1~5번 이미지 문제, 6~15번 텍스트 문제)
        List<String> imageQuestionsTexts = allQuestions.subList(0, 5);
        List<String> textQuestions = allQuestions.subList(5, 15);

        // 3. 이미지 문제 병렬 생성
        List<Map<String, String>> imageQuestions = generateImageQuestions(imageQuestionsTexts, language);

        // 4. 정답 생성
        Map<String, Object> answerResult = generateAnswer(imageQuestions, String.join("\n", textQuestions), language);
        String answerText = (String) answerResult.get("content");

        // 5. 이미지 문제 업로드 후 최종 Workbook 반환
        List<Question> q = awsS3Service.uploadfile(imageQuestions, testMode);
        return workBookService.getWorkBookwithnosum(textQuestions.toString(), answerText, imageQuestions, q, language, category);
    }

    @Override
    public QuestionAnswerResponse reCategoryWorkBook() throws IOException {
        // 마지막 워크북 조회

        Optional<WorkBook> optionalLastWorkBook = workBookRepository.findLastWorkBook();
        WorkBook lastWorkBook = optionalLastWorkBook.orElseThrow(() -> new RuntimeException("기존 문제집이 존재하지 않습니다."));

        // 마지막 문제집의 category와 language 가져오기
        String category = lastWorkBook.getWb_category();
        String language = lastWorkBook.getWb_language();

        // category와 language가 유효한지 확인
        if (category == null || category.trim().isEmpty() || language == null || language.trim().isEmpty()) {
            throw new IllegalArgumentException("카테고리 또는 언어 정보가 유효하지 않습니다.");
        }

        // regenerateCategoryQuestions를 호출하여 새로운 문제 생성
        QuestionAnswerResponse newQuestionsResponse = regenerateCategoryQuestions(category, language);

        // 새로운 문제와 답변을 가져옴
        String newQuestion = newQuestionsResponse.getTextQuestions();
        List<Map<String, String>> imageQuestions = newQuestionsResponse.getImageQuestions();
        String textQuestions = newQuestionsResponse.getTextQuestions();

        // imageQuestions와 textQuestions가 유효한지 확인
        if ((imageQuestions == null || imageQuestions.isEmpty()) && (textQuestions == null || textQuestions.trim().isEmpty())) {
            throw new RuntimeException("문제 생성 실패: imageQuestions 또는 textQuestions가 비어 있습니다.");
        }

        // 답변 생성
        Map<String, Object> answerResult = generateAnswer(imageQuestions, textQuestions,language);
        if (answerResult == null || answerResult.isEmpty()) {
            throw new RuntimeException("답변 생성 실패");
        }

        String answerText = (String) answerResult.get("content");

        // 새로운 문제집 저장
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

    private QuestionAnswerResponse regenerateCategoryQuestions(String category, String language) throws IOException {
        log.debug("카테고리 문제 재생성 시작: " + category);

        String prompt;
        switch (category.toLowerCase()) {
            case "conversation":
                prompt = "Create multiple-choice questions related to common Korean conversations in daily life. Focus on realistic scenarios such as greetings, ordering food, asking for directions, or making small talk. For example, questions could ask which phrase is appropriate in a given situation, or what the appropriate response would be.";
                break;
            case "object":
                prompt = "Generate multiple-choice questions about Korean objects or artifacts that foreign learners might encounter in everyday life. The questions should ask for the Korean name of common objects or traditional items such as hanbok, Korean ceramics, and cultural symbols, as well as everyday items like furniture, clothes, or gadgets.";
                break;
            case "food":
                prompt = "Create multiple-choice quiz questions about Korean food culture. Include common dishes, eating etiquette, and regional specialties. Focus on everyday food items, such as popular street food or dishes found in Korean homes, not necessarily traditional ones.";
                break;
            case "culture":
                prompt = "Generate multiple-choice questions about Korean culture, including popular traditions, festivals, and modern practices. The questions should cover well-known cultural elements, such as holidays like Chuseok or Lunar New Year, as well as aspects of contemporary Korean pop culture.";
                break;
            default:
                throw new IllegalArgumentException("Invalid category: " + category);
        }

        // 이미지 문제 생성
        List<Map<String, String>> imageQuestions = regenerateImageQuestionsByCategory(prompt, language);

        // 텍스트 문제 생성
        String textQuestions = regenerateTextQuestionsByCategory(prompt, language);

        // QuestionAnswerResponse 객체 반환
        QuestionAnswerResponse response = new QuestionAnswerResponse();
        response.setImageQuestions(imageQuestions);
        response.setTextQuestions(textQuestions);

        Map<String, Object> answerResult = generateAnswer(imageQuestions, textQuestions, language);
        String answerText = (String) answerResult.get("content");

        List<Question> q = awsS3Service.uploadfile(imageQuestions, testMode);

        return workBookService.getWorkBookwithnosum(textQuestions, answerText, imageQuestions, q, language, category);
    }



    private String regenerateTextQuestionsByCategory(String categoryPrompt, String language) {
        String questionPrompt =
                "Previously, questions 1 to 5 were generated. Now, using the summarized text, generate 10 multiple-choice questions starting from number 6 to 15. " +
                        "Ensure the numbering begins exactly from 6 and continues sequentially to 15. Do not skip or reset the numbering. " +
                        "Exclude any introductory text and use a formal tone in line with Korean college entrance exam style. " +
                        "Ensure that the questions are distinct and avoid repeating similar concepts or questions. " +
                        "Label the options as ①, ②, ③, and ④, ensuring no answers are provided. If '*' is necessary, use it minimally and not for emphasis. " +
                        "Please create the problem in " + language + " and provide options ①, ②, ③, and ④ in Korean. " + categoryPrompt;

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

    private List<Map<String, String>> regenerateImageQuestionsByCategory(String categoryPrompt, String language) {
        List<Map<String, String>> imageQuestions = new ArrayList<>();
        List<String> previousQuestions = new ArrayList<>(); // 이전에 생성된 문제를 저장할 리스트

        for (int i = 1; i <= 5; i++) {
            // 이전 문제들을 프롬프트에 추가
            String previousQuestionsPrompt = "";
            if (!previousQuestions.isEmpty()) {
                previousQuestionsPrompt = " The following questions have already been generated. Please ensure the new question is different from these: \n";
                for (String prevQuestion : previousQuestions) {
                    previousQuestionsPrompt += "- " + prevQuestion + "\n";
                }
            }
            String questionPrompt =
                    "Using the summarized text, create a single 4-option multiple-choice question numbered " + i +
                            ". The question should be written in a formal tone, similar to Korean college entrance exam questions. " +
                            "Ensure the question is clear, concise, and directly related to the summarized text. " +
                            "The question should not be similar to any previous questions generated. To ensure variety, address different aspects of the topic, use unique perspectives, or rephrase the concepts creatively. " +
                            "Label the answer choices as ①, ②, ③, and ④, and do not include the correct answer. Avoid using special characters like '*' for emphasis. " +
                            "Please write the question in " + language + " and provide the options ①, ②, ③, and ④ in Korean. " +
                            "Here is the summarized text for reference: " + categoryPrompt +
                            "The following questions have already been generated. Please ensure the new question is distinct and not similar to these: \n" + previousQuestionsPrompt +
                            "Ensure the new question is unique, engaging, and tests a different aspect of the topic compared to the previous questions.";


            ChatCompletionDto questionCompletion = ChatCompletionDto.builder()
                    .model("gpt-4o-mini")
                    .messages(List.of(ChatRequestMsgDto.builder()
                            .role("user")
                            .content(questionPrompt)
                            .build()))
                    .build();

            Map<String, Object> questionResponse = executePrompt(questionCompletion);
            String questionText = (String) questionResponse.get("content");

            // 이전 문제와 유사한지 확인 (간단한 예시로 포함 여부만 확인)
            boolean isSimilar = false;
            for (String prevQuestion : previousQuestions) {
                if (questionText.contains(prevQuestion) || prevQuestion.contains(questionText)) {
                    isSimilar = true;
                    break;
                }
            }

            if (isSimilar) {
                i--; // 유사한 문제가 있다면 다시 시도
                continue;
            }

            previousQuestions.add(questionText); // 새로운 문제를 리스트에 추가
            questionText += "\n";

            String imageUrl = generateImage(questionText);

            Map<String, String> questionWithImage = new HashMap<>();
            questionWithImage.put("question", questionText);
            questionWithImage.put("imageUrl", imageUrl);

            imageQuestions.add(questionWithImage);
        }
        return imageQuestions;
    }
}