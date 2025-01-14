package com.stone.microstone.controller.workbook;

import com.stone.microstone.domain.entitiy.WorkBook;

import com.stone.microstone.dto.workbook.WorkBookAnswerResponse;
import com.stone.microstone.dto.workbook.WorkBookResponse;
import com.stone.microstone.dto.chatgpt.QuestionAnswerResponse;
import com.stone.microstone.service.ChatGPTService;
import com.stone.microstone.repository.workbook.WorkBookRepository;
import com.stone.microstone.service.workbook.pdf.PdfService;
import com.stone.microstone.service.workbook.WorkBookService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;

import java.util.*;

//chatgptcontroller와 같으나 세션을 사용하지 않는 웹전용 api.
@Slf4j
@RestController
@RequestMapping("/front/api/workbook")
public class FrontGPTController {
    private final ChatGPTService chatGPTService;
    private final WorkBookService workBookService;
    private final PdfService pdfService;
    private final WorkBookRepository workBookRepository;

    public FrontGPTController(ChatGPTService chatGPTService, WorkBookService workBookService,
                             PdfService pdfService,WorkBookRepository workBookRepository
                             ) {
        this.chatGPTService = chatGPTService;
        this.workBookService = workBookService;
        this.pdfService = pdfService;
        this.workBookRepository = workBookRepository;

    }
    @PostMapping("/processText")
    public ResponseEntity<Map<String, Object>> frontprocessText(@RequestBody String problemText) {
        log.debug("받은 문제 텍스트: " + problemText);

        try {
            QuestionAnswerResponse response = chatGPTService.processText(problemText);
            List<Map<String, String>> imageQuestions = (List<Map<String, String>>) response.getImageQuestions();
            String textQuestions = response.getTextQuestions();

            // 답지 생성
            Map<String, Object> answerResponse = chatGPTService.generateAnswer(imageQuestions, textQuestions);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("imageQuestions", imageQuestions);
            result.put("textQuestions", textQuestions);
            result.put("answerSheet", answerResponse);
            result.put("message", "문제집 생성 완료");


            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.error("입력 오류", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "WorkBook 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PostMapping("/retext") //생성된 문제를 재생성을 수행하는 api
    public ResponseEntity<Object> frontretext(){

        try{ //서비스 수행
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


    @PostMapping("/processCategory")
    public ResponseEntity<Map<String, Object>> processCategory(@RequestParam("category") String category) {
        log.debug("받은 카테고리: " + category);

        try {
            // 카테고리 문제 생성 호출
            QuestionAnswerResponse response = chatGPTService.generateCategoryQuestions(category);

            // 이미지 및 텍스트 문제 추출
            List<Map<String, String>> imageQuestions = (List<Map<String, String>>) response.getImageQuestions();
            String textQuestions = response.getTextQuestions();

            // 답지 생성
            Map<String, Object> answerResponse = chatGPTService.generateAnswer(imageQuestions, textQuestions);

            // 결과 맵 생성
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("imageQuestions", imageQuestions);
            result.put("textQuestions", textQuestions);
            result.put("answerSheet", answerResponse);
            result.put("message", "카테고리 문제집 생성 완료");

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.error("입력 오류", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "카테고리 문제 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @GetMapping("/all") //문제집 전체조회 수행 api
    public ResponseEntity frontbookall(){

        try{ //서비스 수행.전체 조회 list
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
    public ResponseEntity frontanswer(){

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
    public ResponseEntity frontsettingtitle(@RequestParam Integer wb_id,@RequestParam String title){

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
    public ResponseEntity frontsettinganswertitle(@RequestParam Integer wb_id,@RequestParam String title){

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
    public ResponseEntity frontuploadWorkbook(@RequestParam Integer wb_id, @RequestParam("file")MultipartFile file){

        try{
            pdfService.savedata2(file,wb_id);
            return ResponseEntity.ok(Map.of("message","저장완료"));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }

    }

    @PostMapping("/answer/upload") //생성된 답지의 pdf 저장 api
    public ResponseEntity frontuploadanswer(@RequestParam Integer wb_id, @RequestParam("file")MultipartFile file){

        try{
            pdfService.answersavedata2(file,wb_id);
            return ResponseEntity.ok(Map.of("message","저장완료"));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }





    @DeleteMapping("/delete") //문제집 삭제.답지도 함께 삭제됨.
    public ResponseEntity frontworkbookdelete(@RequestParam Integer wb_id){

        try{
            workBookService.deleteSearch(wb_id);
            return ResponseEntity.ok(Map.of("message","삭제완료"));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/download/{wb_id}") //클라이언트에게 저장한 문제집 pdf파일을 전송하는 api
    public ResponseEntity frontdownloadFile(@PathVariable Integer wb_id) {

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
    public ResponseEntity frontdownloadFilean(@PathVariable Integer wb_id) {
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
