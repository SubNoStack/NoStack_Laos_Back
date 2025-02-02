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
            promptUrl = chatGPTConfig.getDalleApiUrl();
            model = ((DalleRequestDto) requestDto).getModel();
        } else if (requestDto instanceof ChatCompletionDto) {
            promptUrl = chatGPTConfig.getApiUrl();
            model = ((ChatCompletionDto) requestDto).getModel();
        } else {
            throw new IllegalArgumentException("Unsupported request type");
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


    @Override //텍스트를 입력받아 문제 텍스트를 처리하고 답변을 생성하는 메서드
    public QuestionAnswerResponse processText(String problemText, String language, String category) throws IOException {

        problemText = cleanInputText(problemText); //특수문자 정제 메소드
        log.debug("받은 문제 텍스트: " + problemText);
        // 문제 텍스트를 처리하여 요약, 문제생성, 답변 생성 3단계를 수행
        // 1단계: 문제 텍스트 요약
        Map<String, Object> summaryResult = summarizeText(problemText, language);
        String summarizedText = (String) summaryResult.get("content");
        if (summarizedText == null || summarizedText.trim().isEmpty()) {
            throw new IllegalArgumentException("요약된 텍스트가 없습니다.");
        }
        log.debug("요약된 텍스트: " + summarizedText);

        // 2단계: 요약된 텍스트로 문제 생성
        Map<String, Object> questionResult = generateQuestion(summarizedText, language);
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
        List<Question> q = awsS3Service.uploadfile(imageQuestions, testMode);

        return workBookService.getWorkBook(textQuestions, summarizedText, answerText, imageQuestions, q, language, category);
    }

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

    @Override
    public Map<String, Object> generateQuestion(String summarizedText, String language) {
        if (summarizedText == null || summarizedText.trim().isEmpty()) {
            log.error("요약된 텍스트가 없습니다.");
            throw new IllegalArgumentException("요약된 텍스트가 없습니다.");
        }

        log.debug("[+] 요약된 텍스트를 기반으로 문제를 생성합니다. 언어: {}", language);

        List<Map<String, String>> imageQuestions = generateImageQuestions(summarizedText, language);
        String textQuestions = generateTextQuestions(summarizedText, language);

        Map<String, Object> result = new HashMap<>();
        result.put("imageQuestions", imageQuestions);
        result.put("textQuestions", textQuestions);

        // 답변 생성 메서드 호출
        Map<String, Object> answerResult = generateAnswer(imageQuestions, textQuestions);
        result.put("answers", answerResult);

        return result;
    }


    private List<Map<String, String>> generateImageQuestions(String summarizedText, String language) {
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
            String questionPrompt = "Using the summarized text, create a formal tone multiple-choice question numbered " + i +
                    " without an introduction. The question should have 4 answer options labeled as ①, ②, ③, and ④. Do not include the correct answer or use any special characters like '*'." +
                    " Create a problem in " + language + " using the following summarized text: " + summarizedText +
                    previousQuestionsPrompt + " Ensure the new question is distinct and not similar to the ones listed above.";

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
/* 이미지문제가 1문제만 생성되도록 수정한 메서드
    private List<Map<String, String>> generateImageQuestions(String summarizedText, String language) {
        List<Map<String, String>> imageQuestions = new ArrayList<>();

        // 1문제만 생성하도록 고정
        String questionPrompt = "Using the summarized text, create a formal tone multiple-choice question numbered 1 " +
                "without an introduction. The question should have 4 answer options labeled as ①, ②, ③, and ④. Do not include the correct answer or use any special characters like '*'." +
                " Create a problem in " + language + " using the following summarized text: " + summarizedText;

        ChatCompletionDto questionCompletion = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content(questionPrompt)
                        .build()))
                .build();

        Map<String, Object> questionResponse = executePrompt(questionCompletion);
        String questionText = (String) questionResponse.get("content");

        questionText += "\n";

        // 이미지 생성 요청 (이미지 1개만 생성)
        String imageUrl = generateImage(questionText);

        Map<String, String> questionWithImage = new HashMap<>();
        questionWithImage.put("question", questionText);
        questionWithImage.put("imageUrl", imageUrl);

        imageQuestions.add(questionWithImage);

        return imageQuestions;
    }*/


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


    private String generateTextQuestions(String summarizedText, String language) {
        ChatCompletionDto textCompletion = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content("Using the summarized text, generate 10 multiple-choice questions numbered 6 through 15. Exclude any introductory text. Use a formal tone in line with Korean college entrance exam style." +
                                " Label the options as ①, ②, ③, and ④, ensuring no answers are provided. If '*' is necessary, use it minimally and not for emphasis. " + "Create a problem using " + language + ". " + summarizedText)
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

        // 설명을 영어로 번역
        // String englishDescription = translateToEnglish(description);

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

//    private String translateToEnglish(String description) {
//        // GPT를 사용하여 설명을 영어로 번역하는 프롬프트 작성
//        ChatCompletionDto translationRequest = ChatCompletionDto.builder()
//                .model("gpt-4o-mini")
//                .messages(List.of(ChatRequestMsgDto.builder()
//                        .role("user")
//                        .content("Translate the following text into English. Provide an accurate and clear translation. \n" + description +
//                                "Additionally, create a detailed image description related to the content of the question." +
//                                " The description should be clear and concise, ensuring that it can be used to generate a visual representation of the topic. Do not use any futuristic, modern, or abstract elements." +
//                                " The image should match the context and tone of the problem described. Avoid using any text in the image.")
//
//                        .build()))
//                .build();
//
//        log.debug("번역 요청 정보={}", translationRequest.toString());
//
//        // GPT로 번역된 텍스트 응답을 받아옴
//        Map<String, Object> translationResponse = executePrompt(translationRequest);
//
//        // 번역된 텍스트 반환
//        return (String) translationResponse.get("content");
//    }

    @Override
    public Map<String, Object> generateAnswer(List<Map<String, String>> imageQuestions, String textQuestions) {
        if ((imageQuestions == null || imageQuestions.isEmpty()) && (textQuestions == null || textQuestions.trim().isEmpty())) {
            log.error("질문 텍스트가 없습니다.");
            throw new IllegalArgumentException("질문 텍스트가 없습니다.");
        }

        log.debug("[+] 질문 텍스트를 기반으로 답변을 생성합니다.");

        // 이미지 질문을 문자열로 변환
        String imageQuestionsString = convertImageQuestionsToString(imageQuestions);

        String combinedQuestions = "문제 (1-5번):\n" + imageQuestionsString + "\n\n문제 (6-15번):\n" + textQuestions;

        ChatCompletionDto chatCompletionDto = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content("Provide the exact answers to the generated questions (1–15) along with detailed explanations limited to four lines each. If a special character is needed, use it sparingly and avoid using it for emphasis." +
                                " Do not include any special characters such as asterisks (*) in the answers or explanations " +
                                " based on the given question." + combinedQuestions)
                        .build()))
                .build();
        log.debug("답변 생성 정보={}", chatCompletionDto.toString());

        Map<String, Object> response = executePrompt(chatCompletionDto);

        if (response == null || response.isEmpty()) {
            throw new RuntimeException("답변 생성 실패");
        }

        return response;
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
        if (questionResult == null || questionResult.isEmpty()) {
            throw new RuntimeException("문제 생성이 실패했습니다.");
        }

        String newQuestion = (String) questionResult.get("content");
        List<Map<String, String>> imageQuestions = (List<Map<String, String>>) questionResult.get("imageQuestions");
        String textQuestions = (String) questionResult.get("textQuestions");
        if ((imageQuestions == null || imageQuestions.isEmpty()) && (textQuestions == null || textQuestions.trim().isEmpty())) {
            throw new RuntimeException("문제 생성 실패: imageQuestions 또는 textQuestions가 비어 있습니다.");
        }


        Map<String, Object> answerResult = generateAnswer(imageQuestions, textQuestions);


        if (answerResult == null || answerResult.isEmpty()) {
            throw new RuntimeException("답변 생성 실패");
        }
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
        if (summarizedText == null || summarizedText.trim().isEmpty()) {
            log.error("요약된 텍스트가 없습니다.");
            throw new IllegalArgumentException("요약된 텍스트가 없습니다.");
        }
        log.debug("[+] 기존 문제들을 기반으로 새로운 문제를 생성합니다.");

        try {
            // 기존 문제를 참고하여 새로운 이미지 기반 문제 5개 생성
            List<Map<String, String>> imageQuestions = regenerateImageQuestions(summarizedText, contextText);

            // 기존 문제를 참고하여 새로운 텍스트 기반 문제 10개 생성
            String textQuestions = regenerateTextQuestions(summarizedText, contextText);

            Map<String, Object> result = new HashMap<>();
            result.put("imageQuestions", imageQuestions);
            result.put("textQuestions", textQuestions);

            // 답지 생성
            Map<String, Object> answerResult = generateAnswer(imageQuestions, textQuestions);
            result.put("answers", answerResult);

            return result;

        } catch (Exception e) {
            log.error("Prompt 실행 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("문제 생성 실패", e);
        }
    }

    private List<Map<String, String>> regenerateImageQuestions(String summarizedText, String contextText) {
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
            String questionPrompt = "Based on the summarized text, create a formal tone multiple-choice question numbered " + i +
                    ". The question should not overlap with the following existing questions: " + contextText +
                    ". Ensure a formal tone similar to Korean college entrance exams. Label the choices as ①, ②, ③, and ④, but do not include the correct answer. " +
                    "Summarized Text: " + summarizedText +
                    previousQuestionsPrompt + " Ensure the new question is distinct and not similar to the ones listed above.";

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

            // 이미지 생성 요청
            String imageUrl = generateImage(questionText);

            Map<String, String> questionWithImage = new HashMap<>();
            questionWithImage.put("question", questionText);
            questionWithImage.put("imageUrl", imageUrl);

            imageQuestions.add(questionWithImage);
        }
        return imageQuestions;
    }

    private String regenerateTextQuestions(String summarizedText, String contextText) {
        String questionPrompt = "Based on the summarized text, generate 10 new multiple-choice questions numbered 6 to 15." +
                " Ensure these questions do not overlap with previous ones. Maintain a formal tone similar to Korean college entrance exams." +
                " Label the options as ①, ②, ③, and ④, ensuring no answers are provided." +
                " Previous Questions: " + contextText + " Summarized Text: " + summarizedText;

        ChatCompletionDto textCompletion = ChatCompletionDto.builder()
                .model("gpt-4o-mini")
                .messages(List.of(ChatRequestMsgDto.builder()
                        .role("user")
                        .content(questionPrompt)
                        .build()))
                .build();

        log.debug("재생성 텍스트 문제 요청: {}", textCompletion.toString());

        Map<String, Object> textQuestionsResponse = executePrompt(textCompletion);
        return (String) textQuestionsResponse.get("content");
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
        // 이미지 문제 생성
        List<Map<String, String>> imageQuestions = generateImageQuestionsByCategory(prompt, language);

        // 텍스트 문제 생성
        String textQuestions = generateTextQuestionsByCategory(prompt, language);

        // QuestionAnswerResponse 객체 반환
        QuestionAnswerResponse response = new QuestionAnswerResponse();
        response.setImageQuestions(imageQuestions);
        response.setTextQuestions(textQuestions);

        Map<String, Object> answerResult = generateAnswer(imageQuestions, textQuestions);
        String answerText = (String) answerResult.get("content");

        List<Question> q = awsS3Service.uploadfile(imageQuestions, testMode);

        return workBookService.getWorkBookwithnosum(textQuestions, answerText, imageQuestions, q, language, category);
    }


    private List<Map<String, String>> generateImageQuestionsByCategory(String categoryPrompt, String language) {
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
                            ". without any introductory text. Ensure the question is written in a formal tone, similar to Korean college entrance exam questions. " +
                            "The question should not be similar to any previous questions generated. Ensure variety by addressing different aspects of the topic, using different perspectives, or rephrasing the concepts. " +
                            "Label the answer choices as ①, ②, ③, and ④, and do not include the correct answer. Avoid using special characters like '*' for emphasis. " +
                            "Please write the question in " + language + " and provide the options ①, ②, ③, and ④ in Korean." + categoryPrompt +
                            previousQuestionsPrompt + " Ensure the new question is distinct and not similar to the ones listed above.";


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
    /* 카테고리 이미지문제 1문제만 나오게 하는 메소드
    private List<Map<String, String>> generateImageQuestionsByCategory(String categoryPrompt, String language) {
        List<Map<String, String>> imageQuestions = new ArrayList<>();
            String questionPrompt =
                    "Using the summarized text, create a single 4-option multiple-choice question numbered 1 " +
                            ". without any introductory text. Ensure the question is written in a formal tone, similar to Korean college entrance exam questions. " +
                            "The question should not be similar to any previous questions generated. Ensure variety by addressing different aspects of the topic, using different perspectives, or rephrasing the concepts. " +
                            "Label the answer choices as ①, ②, ③, and ④, and do not include the correct answer. Avoid using special characters like '*' for emphasis. " +
                            "Please write the question in " + language + " and provide the options ①, ②, ③, and ④ in Korean." + categoryPrompt ;


            ChatCompletionDto questionCompletion = ChatCompletionDto.builder()
                    .model("gpt-4o-mini")
                    .messages(List.of(ChatRequestMsgDto.builder()
                            .role("user")
                            .content(questionPrompt)
                            .build()))
                    .build();

            Map<String, Object> questionResponse = executePrompt(questionCompletion);
            String questionText = (String) questionResponse.get("content");

            questionText += "\n";

            String imageUrl = generateImage(questionText);

            Map<String, String> questionWithImage = new HashMap<>();
            questionWithImage.put("question", questionText);
            questionWithImage.put("imageUrl", imageUrl);

            imageQuestions.add(questionWithImage);

            return imageQuestions;
    }*/

    private String generateTextQuestionsByCategory(String categoryPrompt, String language) {
        String questionPrompt =
                "Using the summarized text, generate 10 multiple-choice questions numbered 6 through 15. Exclude any introductory text. Use a formal tone in line with Korean college entrance exam style." + " Ensure that the questions are distinct and avoid repeating similar concepts or questions. " +
                        " Label the options as ①, ②, ③, and ④, ensuring no answers are provided. If '*' is necessary, use it minimally and not for emphasis. " + "Please create the problem in " + language + " and provide options ①, ②, ③, and ④ in Korean. " + categoryPrompt;

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

    @Override
    public QuestionAnswerResponse reCategoryWorkBook() throws IOException {
        // 마지막 워크북 조회

        Optional<WorkBook> optionalLastWorkBook = workBookRepository.findLastWorkBook();
        WorkBook lastWorkBook = optionalLastWorkBook.orElseThrow(() -> new RuntimeException("기존 문제집이 존재하지 않습니다."));

        // 마지막 문제집의 category와 language 가져오기
        String category = lastWorkBook.getWb_category();
        String language = lastWorkBook.getWb_language();
        String contextText = lastWorkBook.getWb_content();

        // category와 language가 유효한지 확인
        if (category == null || category.trim().isEmpty() || language == null || language.trim().isEmpty()) {
            throw new IllegalArgumentException("카테고리 또는 언어 정보가 유효하지 않습니다.");
        }

        if (contextText == null || contextText.trim().isEmpty()) {
            throw new IllegalArgumentException("문제를 재생성할 데이터가 부족합니다.");
        }

        // regenerateCategoryQuestions를 호출하여 새로운 문제 생성
        QuestionAnswerResponse newQuestionsResponse = regenerateCategoryQuestions(category, language, contextText);

        // 새로운 문제와 답변을 가져옴
        String newQuestion = newQuestionsResponse.getTextQuestions();
        List<Map<String, String>> imageQuestions = newQuestionsResponse.getImageQuestions();
        String textQuestions = newQuestionsResponse.getTextQuestions();

        // imageQuestions와 textQuestions가 유효한지 확인
        if ((imageQuestions == null || imageQuestions.isEmpty()) && (textQuestions == null || textQuestions.trim().isEmpty())) {
            throw new RuntimeException("문제 생성 실패: imageQuestions 또는 textQuestions가 비어 있습니다.");
        }

        // 답변 생성
        Map<String, Object> answerResult = generateAnswer(imageQuestions, textQuestions);
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

    private QuestionAnswerResponse regenerateCategoryQuestions(String category, String language, String contextText) throws IOException {
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

        // 이전 문제를 포함한 프롬프트 생성
        String fullPrompt =  prompt + "\n\n" +
                "[The following questions have already been generated. Please ensure the new questions are distinct and not similar to these: \n" +
                contextText + "]";

        // 이미지 문제 생성
        List<Map<String, String>> imageQuestions = regenerateImageQuestionsByCategory(fullPrompt, language);

        // 텍스트 문제 생성
        String textQuestions = regenerateTextQuestionsByCategory(fullPrompt, language);

        // QuestionAnswerResponse 객체 반환
        QuestionAnswerResponse response = new QuestionAnswerResponse();
        response.setImageQuestions(imageQuestions);
        response.setTextQuestions(textQuestions);

        Map<String, Object> answerResult = generateAnswer(imageQuestions, textQuestions);
        String answerText = (String) answerResult.get("content");

        List<Question> q = awsS3Service.uploadfile(imageQuestions, testMode);

        return workBookService.getWorkBookwithnosum(textQuestions, answerText, imageQuestions, q, language, category);
    }



    private String regenerateTextQuestionsByCategory(String categoryPrompt, String language) {
        String questionPrompt =
                "Using the summarized text, generate 10 multiple-choice questions numbered 6 through 15. Exclude any introductory text. Use a formal tone in line with Korean college entrance exam style." +
                        " Ensure that the questions are distinct and avoid repeating similar concepts or questions. " +
                        " Label the options as ①, ②, ③, and ④, ensuring no answers are provided. If '*' is necessary, use it minimally and not for emphasis. " +
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