package com.stone.microstone.controller.workbook;

import com.stone.microstone.domain.entitiy.WorkBook;

import com.stone.microstone.dto.workbook.*;

import com.stone.microstone.dto.chatgpt.QuestionAnswerResponse;
import com.stone.microstone.service.ChatGPTService;
import com.stone.microstone.repository.workbook.WorkBookRepository;
import com.stone.microstone.service.workbook.pdf.PdfService;
import com.stone.microstone.service.workbook.WorkBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.Resource;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.io.FileNotFoundException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@RestController
@RequestMapping(value = "/api/workbook")  // API to perform workbook-related functions after validation using a session
public class ChatGPTController {

    private final ChatGPTService chatGPTService;
    private final WorkBookService workBookService;
    private final PdfService pdfService;
    private final WorkBookRepository workBookRepository;

    public ChatGPTController(ChatGPTService chatGPTService, WorkBookService workBookService,
                             PdfService pdfService, WorkBookRepository workBookRepository) {
        this.chatGPTService = chatGPTService;
        this.workBookService = workBookService;
        this.pdfService = pdfService;
        this.workBookRepository = workBookRepository;
    }

    @PostMapping("/processText")
    @Operation(summary = "API to process problem text sent by the user", description = "Generate after sending the problem. Note!! 'message' tag exists in the top-level JSON tag.")
    @ApiResponse(responseCode = "200", description = "Success",
            content = {@Content(schema = @Schema(implementation = QuestionAnswerResponse.class))})
    @ApiResponse(responseCode = "400", description = "Input error",
            content = @Content(schema = @Schema(type = "object", example = "{\"error\": \"Client error message\"}")))
    @ApiResponse(responseCode = "500", description = "Server error",
            content = @Content(schema = @Schema(type = "object", example = "{\"error\": \"Internal server error message\"}")))
    public ResponseEntity<Map<String, Object>> processText(
            @Parameter(name="language", description = "Specify the language to generate in, e.g., english, korea, Lao language", example="Lao language", required = true)
            @RequestParam(name = "language") String language,
            @Parameter(name = "category", description = "Problem category e.g., conversation, object, food, culture", example = "object", required = false)
            @RequestParam(name = "category", required = false, defaultValue = "null") String category,
            @RequestBody @Valid RequestBodys Text) {

        try { // Process the received problem text and perform the service
            QuestionAnswerResponse response = chatGPTService.processText(Text.getProblemText(), language, category);
            return new ResponseEntity<>(Map.of("message", response), HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.error("Input error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error occurred", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An error occurred while processing the WorkBook: " + e.getMessage()));
        }

}


    @Operation(summary = "General regeneration API", description = "No parameters required. Note!! 'message' tag exists in the top-level JSON tag.")
    @ApiResponse(responseCode = "200", description = "Success", content = {@Content(schema = @Schema(implementation = QuestionAnswerResponse.class))})
    @ApiResponse(responseCode = "500", description = "Server error", content = {@Content(schema = @Schema(implementation = ErrorResponse.class))})
    @PostMapping("/retext")
    public ResponseEntity<Object> retext() {
        try {
            // Perform the service
            QuestionAnswerResponse response = chatGPTService.getRetextWorkBook();
            if (response == null) {
                log.error("The response is null.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "There is no regenerated workbook."));
            }
            // Return the result
            return new ResponseEntity<>(Map.of("message", response), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error occurred: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An error occurred while saving the WorkBook." + e.getMessage()));
        }
    }


    @Operation(summary = "Category regeneration API", description = "No parameters required. Note!! 'message' tag exists in the top-level JSON tag.")
    @ApiResponse(responseCode = "200", description = "Success", content = {@Content(schema = @Schema(implementation = QuestionAnswerResponse.class))})
    @ApiResponse(responseCode = "500", description = "Server error", content = {@Content(schema = @Schema(implementation = ErrorResponse.class))})
    @PostMapping("/reCategorytext")
    public ResponseEntity<Object> reCategoryText() {
        try {
            QuestionAnswerResponse response = chatGPTService.reCategoryWorkBook();
            if (response == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "There is no regenerated category workbook."));
            }
            return new ResponseEntity<>(Map.of("message", response), HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An error occurred while regenerating the category WorkBook: " + e.getMessage()));
        }
    }


    @Operation(summary = "API to generate and process category-specific problems", description = "Generate after sending the problem. Note!! 'message' tag exists in the top-level JSON tag.")
    @ApiResponse(responseCode = "200", description = "Success",
            content = {@Content(schema = @Schema(implementation = QuestionAnswerResponse.class))})
    @ApiResponse(responseCode = "400", description = "Input error",
            content = @Content(schema = @Schema(type = "object", example = "{\"error\": \"Client error message\"}")))
    @ApiResponse(responseCode = "500", description = "Server error",
            content = @Content(schema = @Schema(type = "object", example = "{\"error\": \"Internal server error message\"}")))
    @PostMapping("/processCategory")
    public ResponseEntity<Map<String, Object>> processCategorys(
            @Parameter(name="category",
                    description = "Decide which category to use, e.g., conversation, object, food, culture", example="object", required = true)
            @RequestParam(name = "category") String category,
            @Parameter(name="language", description = "Specify the language to generate in, e.g., english, korea, Lao language", example="korea", required = true)
            @RequestParam(name = "language", required = false) String language) {
        log.debug("Received category: " + category);

        try {
            // Call category problem generation
            QuestionAnswerResponse response = chatGPTService.generateCategoryQuestions(category, language);
            return new ResponseEntity<>(Map.of("message", response), HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            log.error("Input error", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error occurred", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An error occurred while processing category problems: " + e.getMessage()));
        }
    }


    @Operation(summary = "전체 문제 조회 api",description = "파라미터 필요x주의!!최상단 json태그에 data태그 존재.")
    @ApiResponse(responseCode="200",description = "성공",
            content = {@Content(
                    array = @ArraySchema(schema = @Schema(implementation = WorkBookResponse.class)))})
    @ApiResponse(responseCode = "500", description = "서버 오류",
            content = @Content(schema = @Schema(type = "object", example = "{\"error\": \"서버 내부 오류 메시지\"}")))
    @GetMapping("/all")  //문제집 전체조회 수행 api
    public ResponseEntity bookall(){
        try{
            //서비스 수행.전체 조회닌 list
            List<WorkBookResponse> allbook=workBookService.getAllWorkBook();
            if(allbook.isEmpty()){
                return ResponseEntity.ok(Map.of("data", Collections.emptyList()));
            }
            return ResponseEntity.ok(Map.of("data", allbook));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }

    }

    @Operation(summary = "전체 문제 답지 제목 조회 api", description = "파라미터 필요x, 주의!!최상단 json태그에 data태그 존재.")
    @ApiResponse(responseCode = "200", description = "성공",
            content = {@Content(schema = @Schema(type = "object",
                    example = "{\"data\": [{\"wb_title\": \"문제집2\"}, {\"wb_title\": \"문제집3\"}]}"
            ))})
    @ApiResponse(responseCode = "500", description = "서버 오류",
            content = @Content(schema = @Schema(type = "object", example = "{\"error\": \"서버 내부 오류 메시지\"}")))
    @GetMapping("/answer/all")
    public ResponseEntity answer(){
        try{
            List<WorkBookAnswerResponse> allbook = workBookService.getAllAnswerWorkBook();
            if(allbook.isEmpty()){
                return ResponseEntity.ok(Map.of("data", Collections.emptyList()));
            }
            List<Map<String, String>> wbTitles = allbook.stream()
                    .map(book -> Map.of("wb_title", book.getWb_title()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("data", wbTitles));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "문제집 제목 변경 api",description = "파라미터 두개 필요,주의!!최상단 json태그에 data태그 존재.")
    @ApiResponse(responseCode="200",description = "성공",
            content = {@Content(schema = @Schema(implementation = TitleDto.class))})
    @ApiResponse(responseCode = "500", description = "서버 오류",
            content = @Content(schema = @Schema(type = "object", example = "{\"error\": \"서버 내부 오류 메시지\"}")))
    @PatchMapping("/title") //문제집 제목 변경 api
    public ResponseEntity settingtitle(
            @Parameter(name="wb_id",
                    description = "어느 문제집 제목 바꿀지 결정",example="2",required = true)
            @RequestParam Integer wb_id,
            @Parameter(name="title",
                    description = "제목 변경 문자열 작성",example="새로운문제집",required = true)
            @RequestParam String title){  //생성된 문제 id와 변경할 제목 작성.

        try{
            WorkBook workBook=workBookService.findSearchAndtitle(wb_id,title);
            TitleDto titleDto=new TitleDto("변경 완료",workBook.getWb_id(),workBook.getWb_title());
            return ResponseEntity.ok(Map.of("data",title));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "문제집pdf 업로드 api", description = "파라미터 두개 필요, 완료시 그냥 성공메세지만 전송.")
    @ApiResponse(responseCode = "200", description = "성공적으로 저장됨",
            content = @Content(schema = @Schema(type = "string", example = "{\"message\": \"저장완료\"}")))
    @ApiResponse(responseCode = "500", description = "서버 오류",
            content = @Content(schema = @Schema(type = "object", example = "{\"error\": \"서버 내부 오류 메시지\"}")))
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadWorkbook(
            @Parameter(name = "wb_id", description = "어느 문제집 pdf 업로드 결정", example = "2", required = true)
            @RequestParam Integer wb_id,
            @Valid @ModelAttribute RequestBodys_file pdf_file) {
        try {
            pdfService.savedata2(pdf_file.getFile(), wb_id);
            return ResponseEntity.ok(Map.of("message", "저장완료"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "답지 pdf 업로드 api", description = "파라미터 두개 필요")
    @ApiResponse(responseCode = "200", description = "성공적으로 저장됨",
            content = @Content(schema = @Schema(type = "string", example = "{\"message\": \"저장완료\"}")))
    @ApiResponse(responseCode = "500", description = "서버 오류",
            content = @Content(schema = @Schema(type = "object", example = "{\"error\": \"서버 내부 오류 메시지\"}")))
    @PostMapping(value = "/answer/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadanswer(
            @Parameter(name = "wb_id", description = "어느 답지 pdf 업로드 결정", example = "2", required = true)
            @RequestParam Integer wb_id,
            @Valid @ModelAttribute RequestBodys_file pdf_file) {
        try {
            pdfService.answersavedata2(pdf_file.getFile(), wb_id);
            return ResponseEntity.ok(Map.of("message", "저장완료"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }



    @Operation(summary = "삭제 api",description = "삭제시 관련된 데이터 전부 삭제")
    @ApiResponse(responseCode = "200", description = "성공적으로 저장됨",
            content = @Content(schema = @Schema(type = "string", example = "{\"message\": \"삭제완료\"}")))
    @ApiResponse(responseCode = "500", description = "서버 오류",
            content = @Content(schema = @Schema(type = "object", example = "{\"error\": \"서버 내부 오류 메시지\"}")))
    @DeleteMapping("/delete") //문제집 삭제.답지도 함께 삭제됨.
    public ResponseEntity workbookdelete(
            @Parameter(name="wb_id",
                    description = "삭제할 문제집 번호",example="2",required = true)
            @RequestParam Integer wb_id){ //삭제할 문제 id
        try{
            workBookService.deleteSearch(wb_id);
            return ResponseEntity.ok(Map.of("message","삭제완료"));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "문제집 다운로드 api",description = "경로 파라미터 필요")
    @ApiResponse(responseCode="200",description = "성공",
            content = {@Content(mediaType = "application/pdf",
                    schema = @Schema(type="pdf파일",format = "binary"))})
//    @ApiResponse(responseCode = "400",description = "입력오류",
//            content = {@Content(schema = @Schema(implementation = ErrorResponse.class))})
    @ApiResponse(responseCode = "500", description = "서버 오류",
            content = @Content(schema = @Schema(type = "object", example = "{\"error\": \"서버 내부 오류 메시지\"}")))
    @GetMapping("/download/{wb_id}") //클라이언트에게 저장한 문제집 pdf파일을 전송하는 api
    public ResponseEntity downloadFile(
            @Schema(description = "경로를 통해 문제집 pdf 조회",example = "1")
            @PathVariable Integer wb_id) { //요청할 문제 id

        try{
            Resource resource = workBookService.getResourcework(wb_id);
            if (!resource.exists()) {
                throw new FileNotFoundException("파일을 찾을수 없음: ");
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,"attachment;filename=\""+resource.getFilename()+"\"")
                            .body(resource);

        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "문제집 다운로드 api",description = "경로 파라미터 필요")
    @ApiResponse(responseCode="200",description = "성공",
            content = {@Content(mediaType = "application/pdf",
                    schema = @Schema(type="pdf파일",format = "binary"))})
    @ApiResponse(responseCode = "500", description = "서버 오류",
            content = @Content(schema = @Schema(type = "object", example = "{\"error\": \"서버 내부 오류 메시지\"}")))
    @GetMapping("/answer/download/{wb_id}") //클라이언트에게 저장한 답지 pdf파일을 전송하는 api
    public ResponseEntity downloadFilean(
            @Schema(description = "경로를 통해 문제집 pdf 조회",example = "1")
            @PathVariable Integer wb_id) {//요청할 답지 id

        try{
            Resource resource = workBookService.getResourceanswer(wb_id);
            if (!resource.exists()) {
                throw new FileNotFoundException("파일을 찾을수 없음: ");
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,"attachment;filename=\""+resource.getFilename()+"\"")
                    .body(resource);

        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}