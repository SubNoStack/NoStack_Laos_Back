package com.stone.microstone.controller.workbook;

import com.stone.microstone.domain.entitiy.WorkBook;

import com.stone.microstone.dto.workbook.WorkBookAnswerResponse;
import com.stone.microstone.dto.workbook.WorkBookResponse;
import com.stone.microstone.dto.chatgpt.QuestionAnswerResponse;
import com.stone.microstone.service.ChatGPTService;
import com.stone.microstone.repository.workbook.WorkBookRepository;
import com.stone.microstone.service.workbook.pdf.PdfService;
import com.stone.microstone.service.workbook.WorkBookService;
import jakarta.servlet.http.HttpSession;
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

//chatgptcontroller와 같으나 세션을 사용하지 않는 웹전용 api.
@Slf4j
@RestController
@RequestMapping("/front/api/workbook")
public class FrontGPTController {
    private final ChatGPTService chatGPTService;
    private final WorkBookService workBookService;
    private final PdfService pdfService;
    private final WorkBookRepository workBookRepository;
    private final HttpSession httpSession;

    public FrontGPTController(ChatGPTService chatGPTService, WorkBookService workBookService,
                             PdfService pdfService,WorkBookRepository workBookRepository,
                             HttpSession httpSession) {
        this.chatGPTService = chatGPTService;
        this.workBookService = workBookService;
        this.pdfService = pdfService;
        this.workBookRepository = workBookRepository;
        this.httpSession = httpSession;
    }
    @PostMapping("/processText") //사용자가 보낸 문제 텍스트를 처리하는 api
    public ResponseEntity<Map<String, Object>> frontprocessText(@RequestBody String problemText, @RequestParam(name = "userId", required = false) Integer userId) {
        log.debug("받은 문제 텍스트: " + problemText);

        try { //전달받은 문제 텍스트 처리하여 서비스 수행
            QuestionAnswerResponse response = chatGPTService.processText(problemText, userId);
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
    public ResponseEntity<Object> frontretext(@RequestParam Integer userId){

        try{ //서비스 수행
            QuestionAnswerResponse response=chatGPTService.getRetextWorkBook(userId);
            // 결과 반환
            return new ResponseEntity<>(Map.of("message",response), HttpStatus.OK);
            // 결과 반환
        }catch(Exception e){
            log.error("오류발생");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "WorkBook 저장 중 오류가 발생했습니다."+e.getMessage()));
        }
    }

    @PatchMapping("/favorite") //문제집 즐겨찾기를 수행하는 api
    public ResponseEntity<Object> frontfavorite(@RequestParam Integer wb_id,@RequestParam Integer userId){

        try{ //서비스 수행
            WorkBook workBook=workBookService.findFavorite(wb_id,userId);
            return new ResponseEntity<>(Map.of("message","즐겨찾기가 완료되었습니다",
                    "wb_id", workBook.getWb_user_id(),"favorite",workBook.isWb_favorite()), HttpStatus.OK);
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/answer/favorite") //답지 즐겨찾기를 수행하는 api
    public ResponseEntity<Object> frontfavoriteanswer(@RequestParam Integer wb_id,@RequestParam Integer userId){

        try{ //서비스 수행
            WorkBook workBook=workBookService.findAnswerFavorite(wb_id,userId);
            return new ResponseEntity<>(Map.of("message","즐겨찾기가 완료되었습니다",
                    "wb_id", workBook.getWb_user_id(),"favorite",workBook.isWb_favorite_answer()), HttpStatus.OK);
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/all") //문제집 전체조회 수행 api
    public ResponseEntity frontbookall(@RequestParam Integer userId){

        try{ //서비스 수행.전체 조회 list
            List<WorkBookResponse> allbook=workBookService.getAllWorkBook(userId);
            if(allbook.isEmpty()){
                return ResponseEntity.ok(Map.of("data", Collections.emptyList()));
            }
            return ResponseEntity.ok(Map.of("data", allbook));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }

    }

    @GetMapping("/favorite/all") //즐겨찾기 문제집 전제조회 api
    public ResponseEntity frontfavoriteall(@RequestParam Integer userId){

        try{
            List<WorkBookResponse> allbook=workBookService.getAllFavoriteWorkBook(userId);
            if(allbook.isEmpty()){
                return ResponseEntity.ok(Map.of("data", Collections.emptyList()));
            }
            return ResponseEntity.ok(Map.of("data",allbook));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }


    @GetMapping("/answer/all") //답지 전체 조회 api
    public ResponseEntity frontanswer(@RequestParam Integer userId){

        try{
            List<WorkBookAnswerResponse> allbook=workBookService.getAllAnswerWorkBook(userId);
            if(allbook.isEmpty()){
                return ResponseEntity.ok(Map.of("data", Collections.emptyList()));
            }
            return ResponseEntity.ok(Map.of("data", allbook));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/favorite/answer/all") //즐겨찾기한 답지 전체 조회
    public ResponseEntity frontanswerfavorite(@RequestParam Integer userId){

        try{
            List<WorkBookAnswerResponse> allbook=workBookService.getAllFavoriteAnswerWorkBook(userId);
            if(allbook.isEmpty()){
                return ResponseEntity.ok(Map.of("data", Collections.emptyList()));
            }
            return ResponseEntity.ok(Map.of("data",allbook));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }



    @PatchMapping("/title") //문제집 제목 변경 api
    public ResponseEntity frontsettingtitle(@RequestParam Integer wb_id,@RequestParam String title,@RequestParam Integer userId){

        try{
            WorkBook workBook=workBookService.findSearchAndtitle(wb_id,userId,title);
            return ResponseEntity.ok(Map.of("message","제목변경 완료",
                    "wb_id",workBook.getWb_user_id(),"wb_title",workBook.getWb_title()));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/answer/title") //답지 제목 변경 api
    public ResponseEntity frontsettinganswertitle(@RequestParam Integer wb_id,@RequestParam String title,@RequestParam Integer userId){

        try{
            WorkBook workBook=workBookService.findSearchAndanswertitle(wb_id,userId,title);
            return ResponseEntity.ok(Map.of("message","제목변경 완료",
                    "wb_id",workBook.getWb_user_id(),"wb_title",workBook.getWb_title_answer()));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }


    @PostMapping("/upload")  //생성된 문제집의 pdf를 저장하는 api
    public ResponseEntity frontuploadWorkbook(@RequestParam Integer wb_id, @RequestParam("file")MultipartFile file,@RequestParam Integer userId){

        try{
            pdfService.savedata2(file,wb_id,userId);
            return ResponseEntity.ok(Map.of("message","저장완료"));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }

    }

    @PostMapping("/answer/upload") //생성된 답지의 pdf 저장 api
    public ResponseEntity frontuploadanswer(@RequestParam Integer wb_id, @RequestParam("file")MultipartFile file,@RequestParam Integer userId){

        try{
            pdfService.answersavedata2(file,wb_id,userId);
            return ResponseEntity.ok(Map.of("message","저장완료"));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }





    @DeleteMapping("/delete") //문제집 삭제.답지도 함께 삭제됨.
    public ResponseEntity frontworkbookdelete(@RequestParam Integer wb_id,@RequestParam Integer userId){

        try{
            workBookService.deleteSearch(wb_id,userId);
            return ResponseEntity.ok(Map.of("message","삭제완료"));
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/download/{wb_id}") //클라이언트에게 저장한 문제집 pdf파일을 전송하는 api
    public ResponseEntity frontdownloadFile(@PathVariable Integer wb_id,@RequestParam Integer userId) {

        try{
            Resource resource = workBookService.getResourcework(wb_id,userId);
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
    public ResponseEntity frontdownloadFilean(@PathVariable Integer wb_id,@RequestParam Integer userId) {
        try{
            Resource resource = workBookService.getResourceanswer(wb_id,userId);
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
