package com.stone.microstone.service.workbook;

import com.stone.microstone.domain.entitiy.WorkBook;
import com.stone.microstone.domain.entitiy.AnswerPDF;
import com.stone.microstone.domain.entitiy.WorkBookPDF;
import com.stone.microstone.dto.workbook.*;
import com.stone.microstone.dto.chatgpt.QuestionAnswerResponse;
import com.stone.microstone.domain.entitiy.LocalUser;
import com.stone.microstone.repository.social.LocalUserRepository;
import com.stone.microstone.repository.workbook.WorkBookRepository;
import com.stone.microstone.service.workbook.pdf.PdfService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

//문제집 컨트롤러에서 수행하는 기능들
@Slf4j
@Service
public class WorkBookService {
    private final WorkBookRepository workBookRepository;
    private final LocalUserRepository userRepository;
    private final PdfService pdfService;


    public WorkBookService(WorkBookRepository workBookRepository,
                           LocalUserRepository userRepository,
                           PdfService pdfService) {
        this.workBookRepository = workBookRepository;
        this.userRepository=userRepository;
        this.pdfService=pdfService;


    }

    //기존의 저장된 문제집을 찾고,문제집 pdf 테이블을 생성한뒤.json 응답을위한 dto 생성후 반환
    @Transactional
    public QuestionAnswerResponse getWorkBook(String Question, String summ, String answer, int userId) throws IOException {
        if (answer == null || answer.trim().isEmpty()) {
            log.error("생성된 답변이 없습니다.");
            throw new RuntimeException("생성된 답변이 존재하지 않음. User ID: " + userId);
        }
        WorkBook saveWorkBook = findAndsaveWorkBook(Question, summ,answer, userId);

        //pdf테이블들 생성.
        pdfService.save(saveWorkBook.getWb_user_id(),userId);
        pdfService.answersave(saveWorkBook.getWb_user_id(),userId);

        //dto
        return new QuestionAnswerResponse(saveWorkBook.getWb_user_id(),saveWorkBook.getWb_title(),Question,answer);
    }

    //유저 정보를 이용하여 유저가 생성한 문제를 저장하기 위해 수행하는 메소드.
    @Transactional
    public WorkBook findAndsaveWorkBook(String content,String sumtext,String answer, int user_id) {
        //유저 찾기
        Optional<LocalUser> userOptional = userRepository.findById(user_id);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));

        //다음 문제 번호 찾고 테이블에 행데이터 넣기.
        int nextid=workBookRepository.findMaxUserid(user).orElse(0)+1;
        String title="문제집 "+nextid;
        String antitle="답안지 "+nextid;
        WorkBook newwork = new WorkBook();
        newwork.setUser(user);
        newwork.setWb_title(title);
        newwork.setWb_content(content);
        newwork.setWb_create(LocalDate.now());
        newwork.setWb_sumtext(sumtext);
        newwork.setWb_favorite(false);
        newwork.setWb_answer(answer);
        newwork.setWb_user_id(nextid);
        newwork.setWb_title_answer(antitle);
        newwork.setWb_favorite_answer(false);
        return workBookRepository.save(newwork);

    }
    //재생성시 마지막에 저장한 문제집을 찾고 다시 문제집을 갱신하는 메소드.
    @Transactional
    public WorkBook findLastWorkBook(String content, String answer,int user_id) {
        //유저를 찾고
        Optional<LocalUser> userOptional = userRepository.findById(user_id);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));

        //마지막에 생성한 문제집 찾는다.
        Optional<WorkBook> newwork = workBookRepository.findLastWorkBook(user);
        WorkBook newworkBook;
        if(newwork.isEmpty()) {
            throw new RuntimeException("기존 문제집이 존재하지 않음. User ID: " + user_id);
        }
        //새로 재생성된 문제를 넣고 저장.
        newworkBook=newwork.get();
        newworkBook.setUser(user);
        newworkBook.setWb_content(content);
        newworkBook.setWb_answer(answer);
        newworkBook.setWb_create(LocalDate.now());

        return workBookRepository.save(newworkBook);

    }

    //재생성한 문제를 저장작업을 수행후 dto를 만든뒤 반환.


    //기존에 생성한 문제집을 가져옴.
//    @Transactional
//    public WorkBook findWorkBook(int user_id) {
//        Optional<User> userOptional = userRepository.findById(user_id);
//        User user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));
//
//        Optional<WorkBook> newwork = workBookRepository.findLastWorkBook(user);
//        //WorkBook newworkBook=newwork.get();
//
//        return newwork.orElseThrow(() -> new RuntimeException("기존 문제집이 존재하지 않음. User ID: " + user_id));
//
//    }
    //즐겨찾기를 하기위해 문제집 행을 가져온뒤 저장후 반환.
    @Transactional
    public WorkBook findFavorite(int wb_id, int user_id){//문제찾고 즐겨찾기
        //유저 찾기
        Optional<LocalUser> userOptional = userRepository.findById(user_id);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));

        //유저의 문제id를 이용하여 문제집을 찾고 문제 즐겨찾기 수행.
        Optional<WorkBook> newwork = workBookRepository.findByuserIdandUser(wb_id,user);
        WorkBook faworkBook=newwork.get();
        faworkBook.setWb_favorite(!faworkBook.isWb_favorite());
        WorkBook workBook=workBookRepository.save(faworkBook);

        return workBook;

    }

    //답지 즐겨찾기를 수행하기 위한 메소드.
    @Transactional
    public  WorkBook findAnswerFavorite(int wb_id,int user_id){//문제찾고 즐겨찾기
        Optional<LocalUser> userOptional = userRepository.findById(user_id);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));

        Optional<WorkBook> newwork = workBookRepository.findByuserIdandUser(wb_id,user);
        WorkBook faworkBook=newwork.orElseThrow(()->new RuntimeException("문제를 찾을 수 없음. ID: "+wb_id));
        faworkBook.setWb_favorite_answer(!faworkBook.isWb_favorite_answer());

        WorkBook workBook = workBookRepository.save(faworkBook);


        return workBook;

    }

    //문제집을 찾는것을 수행하는 메소드.
    @Transactional
    public WorkBook findSearch(int wb_id,int user_id){  //문제찾기
        Optional<LocalUser> userOptional = userRepository.findById(user_id);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));

        //유저정보와 문제id를 통해 문제집 가져오기.
        Optional<WorkBook> newwork = workBookRepository.findByuserIdandUser(wb_id,user);
        WorkBook faworkBook=newwork.orElseThrow(()->new RuntimeException("문제를 찾을 수 없음. ID: "+wb_id));
        //faworkBook.setWb_favorite(true);
        return faworkBook;

    }

    //문제집을 찾은뒤 삭제를 수행하는 메소드
    @Transactional
    public void deleteSearch(int wb_id,int user_id) throws IOException {  //문제찾기
        Optional<LocalUser> userOptional = userRepository.findById(user_id);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));

        //문제집 찾기.
        Optional<WorkBook> workBook = workBookRepository.findByuserIdandUser(wb_id,user);
        WorkBookPDF workBookPDF=pdfService.findByworkBook(workBook.get());
        //저장된 pdf들도 같이 삭제.
        if(workBook != null && workBookPDF.getPdf_path() !=null){
            Path path= Paths.get(workBookPDF.getPdf_path());
            Files.deleteIfExists(path);
        }
        AnswerPDF answerPDF=pdfService.anfindByworkBook(workBook.get());
        if(workBook != null &&answerPDF.getPdf_path() !=null){
            Path path= Paths.get(answerPDF.getPdf_path());
            Files.deleteIfExists(path);
        }
        //db에서 삭제 수행.
        workBookRepository.deleteById(wb_id,user);
        //faworkBook.setWb_favorite(true);
    }

    //제목 변경을 하기위해 문제집을 찾는것을 수행하는 메솓,
    @Transactional
    public WorkBook findSearchAndtitle(int wb_id,int user_id,String title){  //문제찾기
        Optional<LocalUser> userOptional = userRepository.findById(user_id);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));

        //문제집 찾기.
        Optional<WorkBook> newwork = workBookRepository.findByuserIdandUser(wb_id,user);
        WorkBook faworkBook=newwork.orElseThrow(()->new RuntimeException("문제를 찾을 수 없음. ID: "+wb_id));
        //제목 변경.
        faworkBook.setWb_title(title);
        return faworkBook;


    }

    //답지를 찾은뒤 제목 변경을 수행.
    @Transactional
    public WorkBook findSearchAndanswertitle(int wb_id,int user_id,String title){  //문제찾기
        Optional<LocalUser> userOptional = userRepository.findById(user_id);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));

        Optional<WorkBook> newwork = workBookRepository.findByuserIdandUser(wb_id,user);
        WorkBook faworkBook=newwork.orElseThrow(()->new RuntimeException("문제를 찾을 수 없음. ID: "+wb_id));
        faworkBook.setWb_title_answer(title);
        return faworkBook;

    }

    //유저가 생성한 문제집 정보를 전부 찾아 반환을 수행하는 메솓,
    @Transactional
    public List<WorkBook> findWorkBookall(int user_id){ //전체 문제찾기
        Optional<LocalUser> userOptional = userRepository.findById(user_id);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));
        //문제집 찾기.
        List<WorkBook> newwork = workBookRepository.findByUser(user);
        return newwork;
    }

    //문제집 pdf경로가 존재하는것만 문제집정보를 반환
    @Transactional
    public List<WorkBook> findWorkBookallWithpath(int user_id){ //전체 문제찾기 근데 path 없으면 조회x
        Optional<LocalUser> userOptional = userRepository.findById(user_id);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));
        List<WorkBook> newwork = workBookRepository.findByUserAndpdf(user);
        return newwork;
    }

    //유저가 생성한 문제집 정보를 전부 찾아 리스트로 반환하는 메소드,
    @Transactional
    public List<WorkBookResponse> getAllWorkBook(Integer user_id){  /*user_id로 해당 문제들 찾고 그것을 dto로 변환후 반환.*/
        List<WorkBook> worklist=findWorkBookallWithpath(user_id);
        List<Map<String, Object>> pdfbook=pdfService.getPdfsForWorkbook(worklist);
        return worklist.stream()
                .map(book -> convertDTO(book,pdfbook))
                .collect(Collectors.toList());
    }

    //답지의 pdf경로가 존재하는것만 찾아 문제집 정보를 반환
    @Transactional
    public List<WorkBook> findAnswerallWithpath(int user_id){ //전체 문제찾기 근데 path 없으면 조회x
        Optional<LocalUser> userOptional = userRepository.findById(user_id);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));
        List<WorkBook> newwork = workBookRepository.findByUserAndAnsPdf(user);
        return newwork;
    }


    //답지를 전부 찾은뒤 리스트형태로 컨트롤러에 반환을 수행.
    @Transactional
    public List<WorkBookAnswerResponse> getAllAnswerWorkBook(int user_id){
        List<WorkBook> worklist=findAnswerallWithpath(user_id);
        List<Map<String, Object>> pdfbook=pdfService.getPdfsForanswer(worklist);
        return worklist.stream()
                .map(book -> convertAnswerDTO(book,pdfbook))
                .collect(Collectors.toList());
    }


    //실제 문제집 정보를 json으로 반환하기 위해 dto로 변환수행.
    private WorkBookResponse convertDTO(WorkBook pdfbook, List<Map<String, Object>> worklist){ /*controller부분이 길어져 dto로 변환.*/
        Map<String, Object> pdfData = worklist.stream()
                .filter(pdf -> pdf.get("wb_id").equals(pdfbook.getWb_id()))
                .findFirst()
                .orElse(Map.of("workbook_pdf", Map.of("filename", "", "pdf_path", "")));

        PdfDTO pdfdto=new PdfDTO();
        Map<String,Object> workbookpdf=(Map<String,Object>)pdfData.get("workbook_pdf");
        pdfdto.setFileName((String) workbookpdf.get("filename"));
        pdfdto.setFilePath((String) workbookpdf.get("pdf_path"));

        WorkBookResponse dto=new WorkBookResponse();
        dto.setWb_id(pdfbook.getWb_id());
        dto.setWb_title(pdfbook.getWb_title());
        dto.setWb_create(pdfbook.getWb_create());
        dto.setWb_content(pdfbook.getWb_content());
        dto.setFavorite(pdfbook.isWb_favorite());
        dto.setWorkbook_pdf(pdfdto);

        return dto;

    }

    //답지를 json으로 반환하기 위해 dto반환.
    private WorkBookAnswerResponse convertAnswerDTO(WorkBook pdfbook, List<Map<String, Object>> worklist){ /*controller부분이 길어져 dto로 변환.*/
        Map<String, Object> pdfData = worklist.stream()
                .filter(pdf -> pdf.get("wb_id").equals(pdfbook.getWb_id()))
                .findFirst()
                .orElse(Map.of("workbook_pdf", Map.of("filename", "", "pdf_path", "")));

        PdfDTO pdfdto=new PdfDTO();
        Map<String,Object> workbookpdf=(Map<String,Object>)pdfData.get("workbook_pdf");
        pdfdto.setFileName((String) workbookpdf.get("filename"));
        pdfdto.setFilePath((String) workbookpdf.get("pdf_path"));

        WorkBookAnswerResponse dto=new WorkBookAnswerResponse();
        dto.setWb_id(pdfbook.getWb_id());
        dto.setWb_title(pdfbook.getWb_title());
        dto.setWb_create(pdfbook.getWb_create());
        dto.setWb_answer(pdfbook.getWb_answer());
        dto.setFavorite(pdfbook.isWb_favorite());
        dto.setWorkbook_pdf(pdfdto);

        return dto;

    }

    //전체 즐겨찾기 문제찾기
    @Transactional
    public List<WorkBook> findfavoriteWorkBookall(int user_id){
        Optional<LocalUser> userOptional = userRepository.findById(user_id);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));
        List<WorkBook> newwork = workBookRepository.findByUserfavoirte(user);
        return newwork;
    }

    //전체 즐겨찾기를 한 문제집을 찾고 리스트로 컨트롤러에 반환
    @Transactional
    public List<WorkBookResponse> getAllFavoriteWorkBook(int user_id){
        List<WorkBook> allbook=findfavoriteWorkBookall(user_id);
        List<Map<String, Object>> pdfbook=pdfService.getPdfsForWorkbook(allbook);
        return allbook.stream()
                .map(book -> convertDTO(book,pdfbook))
                .collect(Collectors.toList());
    }

    //전체 즐겨찾기를한 문제답지를 컨트롤러로 반환.
    @Transactional
    public List<WorkBookAnswerResponse> getAllFavoriteAnswerWorkBook(int user_id){
        List<WorkBook> allbook=findfavoriteWorkBookall(user_id);
        List<Map<String, Object>> pdfbook=pdfService.getPdfsForanswer(allbook);
        return allbook.stream()
                .map(book -> convertAnswerDTO(book,pdfbook))
                .collect(Collectors.toList());
    }

    //문제집을 찾은뒤 pdf경로를 가져오는 동작을 수행하는 메소드
    @Transactional
    public Resource getResourcework(int wb_id,int userid) throws MalformedURLException {
        //문제집을 찾은뒤
        WorkBook workBook=findSearch(wb_id,userid);
        WorkBookPDF workBookPDF=pdfService.findByworkBook(workBook);
        //저장한 pdf 경로 가져오기.
        String pdfPath=workBookPDF.getPdf_path();
        Path filePath= Paths.get(pdfPath).normalize();
        return new UrlResource(filePath.toUri());
    }

    //답지를 찾은뒤 pdf경로를 가져오기.
    @Transactional
    public Resource getResourceanswer(int wb_id,int userid) throws MalformedURLException {
        WorkBook workBook=findSearch(wb_id,userid);
        AnswerPDF answerPDF=pdfService.anfindByworkBook(workBook);
        String pdfPath=answerPDF.getPdf_path();
        Path filePath= Paths.get(pdfPath).normalize();
        return new UrlResource(filePath.toUri());
    }

    @Transactional
    public void allbookdelete(){
        workBookRepository.deleteAll();
    }

}
