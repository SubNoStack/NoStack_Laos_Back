package com.stone.microstone.controller.workbook;

import com.stone.microstone.domain.entitiy.WorkBook;

import com.stone.microstone.dto.workbook.ErrorResponse;
import com.stone.microstone.dto.workbook.RequestBodys;
import com.stone.microstone.dto.workbook.WorkBookAnswerResponse;
import com.stone.microstone.dto.workbook.WorkBookResponse;

import com.stone.microstone.dto.chatgpt.QuestionAnswerResponse;
import com.stone.microstone.service.ChatGPTService;
import com.stone.microstone.repository.workbook.WorkBookRepository;
import com.stone.microstone.service.workbook.pdf.PdfService;
import com.stone.microstone.service.workbook.WorkBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import java.util.List;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping(value = "/api/workbook")  //세션을 사용하여 검증한뒤 문제집과 관련된 기능을 수행하는 api
public class ChatGPTController {

    private final ChatGPTService chatGPTService;
    private final WorkBookService workBookService;
    private final PdfService pdfService;
    private final WorkBookRepository workBookRepository;

    public ChatGPTController(ChatGPTService chatGPTService, WorkBookService workBookService,
                             PdfService pdfService,WorkBookRepository workBookRepository) {
        this.chatGPTService = chatGPTService;
        this.workBookService = workBookService;
        this.pdfService = pdfService;
        this.workBookRepository = workBookRepository;
    }

    @PostMapping("/processText") //사용자가 보낸 문제 텍스트를 처리하는 api
    @Operation(summary = "사용자가 보낸 문제 텍스트를 처리하는 api",description = "문제를 전송후 생성.")
    @ApiResponse(responseCode="200",description = "성공",
    content = {@Content(schema = @Schema(implementation = QuestionAnswerResponse.class))})
    @ApiResponse(responseCode = "400",description = "잘못된 요청",
    content = {@Content(schema = @Schema(implementation = ErrorResponse.class))})
    @ApiResponse(responseCode = "500",description = "서버오류",
    content = {@Content(schema = @Schema(implementation = ErrorResponse.class))})
    public ResponseEntity<Map<String, Object>> processText(
            @Parameter(name="paramValue",description = "어느나라 언어로 생성할건지 작성ex)en,ko,laos",example="ko",required = true)
            @RequestParam String language,
            @RequestBody @Valid RequestBodys Text) {

        try { //전달받은 문제 텍스트 처리하여 서비스 수행
            QuestionAnswerResponse response = chatGPTService.processText(Text.getProblemText(),language);
            return new ResponseEntity<>(Map.of("message", response), HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            log.error("입력 오류", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("오류 발생", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "WorkBook 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }



    @PostMapping("/retext") //생성된 문제를 재생성을 수행하는 api
    public ResponseEntity<Object> retext(){

        try{
            //서비스 수행
            QuestionAnswerResponse response=chatGPTService.getRetextWorkBook();
            // 결과 반환
            return new ResponseEntity<>(Map.of("message",response), HttpStatus.OK);
            // 결과 반환
        }catch(Exception e){
            log.error("오류발생");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "WorkBook 저장 중 오류가 발생했습니다."+e.getMessage()));
        }
    }



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





    @GetMapping("/answer/all") //답지 전체 조회 api
    public ResponseEntity answer(){

        try{
            List<WorkBookAnswerResponse> allbook=workBookService.getAllAnswerWorkBook();
            if(allbook.isEmpty()){
                return ResponseEntity.ok(Map.of("data", Collections.emptyList()));
            }
            return ResponseEntity.ok(Map.of("data", allbook));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }



    @PatchMapping("/title") //문제집 제목 변경 api
    public ResponseEntity settingtitle(@RequestParam Integer wb_id,@RequestParam String title){  //생성된 문제 id와 변경할 제목 작성.

        try{
            WorkBook workBook=workBookService.findSearchAndtitle(wb_id,title);
            return ResponseEntity.ok(Map.of("message","제목변경 완료",
                    "wb_id",workBook.getWb_id(),"wb_title",workBook.getWb_title()));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/answer/title") //답지 제목 변경 api
    public ResponseEntity settinganswertitle(@RequestParam Integer wb_id,@RequestParam String title){ //생성된 답지 id와 변경할 제목 작성.

        try{
            WorkBook workBook=workBookService.findSearchAndanswertitle(wb_id,title);
            return ResponseEntity.ok(Map.of("message","제목변경 완료",
                    "wb_id",workBook.getWb_id(),"wb_title",workBook.getWb_title_answer()));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }


    @PostMapping("/upload")  //생성된 문제집의 pdf를 저장하는 api 
    public ResponseEntity uploadWorkbook(@RequestParam Integer wb_id, @RequestParam("file")MultipartFile file){ //생성된 문제 id와 pdf파일.
        try{
            //pdf만 저장하니 서비스만 수행
            pdfService.savedata2(file,wb_id);
            return ResponseEntity.ok(Map.of("message","저장완료"));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }

    }

    @PostMapping("/answer/upload") //생성된 답지의 pdf 저장 api
    public ResponseEntity uploadanswer(@RequestParam Integer wb_id, @RequestParam("file")MultipartFile file){//생성된 답지 id와 pdf파일.

        try{
            //pdf만 저장하니 서비스만 수행
            pdfService.answersavedata2(file,wb_id);
            return ResponseEntity.ok(Map.of("message","저장완료"));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }


    @DeleteMapping("/delete") //문제집 삭제.답지도 함께 삭제됨.
    public ResponseEntity workbookdelete(@RequestParam Integer wb_id){ //삭제할 문제 id

        try{
            workBookService.deleteSearch(wb_id);
            return ResponseEntity.ok(Map.of("message","삭제완료"));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/download/{wb_id}") //클라이언트에게 저장한 문제집 pdf파일을 전송하는 api
    public ResponseEntity downloadFile(@PathVariable Integer wb_id) { //요청할 문제 id

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

    @GetMapping("/answer/download/{wb_id}") //클라이언트에게 저장한 답지 pdf파일을 전송하는 api
    public ResponseEntity downloadFilean(@PathVariable Integer wb_id) {//요청할 답지 id

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