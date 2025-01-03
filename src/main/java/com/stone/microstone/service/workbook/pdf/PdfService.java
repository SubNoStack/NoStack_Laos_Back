package com.stone.microstone.service.workbook.pdf;

import com.stone.microstone.domain.entitiy.WorkBook;
import com.stone.microstone.domain.entitiy.AnswerPDF;
import com.stone.microstone.domain.entitiy.WorkBookPDF;
import com.stone.microstone.domain.entitiy.LocalUser;
import com.stone.microstone.repository.social.LocalUserRepository;
import com.stone.microstone.repository.workbook.pdf.AnswerPdfRepository;
import com.stone.microstone.repository.workbook.pdf.WorkBookPdfRepository;
import com.stone.microstone.repository.workbook.WorkBookRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PdfService {

    private WorkBookPdfRepository workbookPdfRepository;
    private AnswerPdfRepository answerPdfRepository;
    private WorkBookRepository workbookRepository;
    private LocalUserRepository userRepository;
    private static final String UPLOAD_DIR="uploads";

    @Autowired
    public PdfService(WorkBookPdfRepository workbookPdfRepository, AnswerPdfRepository answerPdfRepository, WorkBookRepository workbookRepository, LocalUserRepository userRepository) {
        this.workbookPdfRepository = workbookPdfRepository;
        this.answerPdfRepository = answerPdfRepository;
        this.workbookRepository = workbookRepository;
        this.userRepository = userRepository;
    }

    //pdf 테이블에 데이터를 저장.
    @Transactional
    public WorkBookPDF save(int wb_id, int user_id) throws IOException {
        Optional<LocalUser> userOptional = userRepository.findById(user_id);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));
        //문제집을 문제id와 유저 정보로 찾는것.
        WorkBook workBook = workbookRepository.findByuserIdandUser(wb_id,user).orElseThrow(() -> new RuntimeException("문제집이 존재하지 않음"));
        //pdf테이블 생성.
        WorkBookPDF pdf=new WorkBookPDF();
        pdf.setWorkBook(workBook);
        return workbookPdfRepository.save(pdf);
    }

    //pdf 테이블에 데이터를 저장후 반환..
    @Transactional
    public WorkBookPDF savedata2(MultipartFile file, int wb_id,int user_id) throws IOException {
        Optional<LocalUser> userOptional = userRepository.findById(user_id);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));

        WorkBook workBook = workbookRepository.findByuserIdandUser(wb_id,user).orElseThrow(() -> new RuntimeException("문제집이 존재하지 않음"));
        WorkBookPDF pdf=workbookPdfRepository.findByWorkBook(workBook);
        //파일 경로 생성한뒤 반환.
        String filePath=savePath(file, file.getOriginalFilename());
        pdf.setFileName(file.getOriginalFilename());
        pdf.setPdf_path(filePath);
        return workbookPdfRepository.save(pdf);
    }

    //문제집 정보를 통해 pdf테이블 가져오기.
    @Transactional
    public WorkBookPDF findByworkBook(WorkBook wb) {
        return workbookPdfRepository.findByWorkBook(wb);
    }

    //비어있는 답지pdf 테이블을 생성해 db에 저장을 수행.
    @Transactional
    public AnswerPDF answersave(int wb_id, int user_id) throws IOException {
        Optional<LocalUser> userOptional = userRepository.findById(user_id);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));

        WorkBook workBook = workbookRepository.findByuserIdandUser(wb_id,user).orElseThrow(() -> new RuntimeException("문제집이 존재하지 않음"));

        AnswerPDF answerPDF=new AnswerPDF();

        answerPDF.setWorkBook(workBook);
        return answerPdfRepository.save(answerPDF);
    }


    //실제 데이터를 pc에 저장한뒤 경로정보를 저장.
    @Transactional
    public AnswerPDF answersavedata2(MultipartFile file, int wb_id,int user_id) throws IOException {
        Optional<LocalUser> userOptional = userRepository.findById(user_id);
        LocalUser user = userOptional.orElseThrow(() -> new RuntimeException("유저를 찾을 수 없음. ID: " + user_id));

        WorkBook workBook = workbookRepository.findByuserIdandUser(wb_id,user).orElseThrow(() -> new RuntimeException("문제집이 존재하지 않음"));

        //이미 생성된 pdf를 생성한뒤.
        AnswerPDF answerPDF=answerPdfRepository.findByWorkBook(workBook);
        //특정 경로에 이미지를 저장.
        String filePath=savePath(file, file.getOriginalFilename());
        answerPDF.setFileName(file.getOriginalFilename());
        answerPDF.setPdf_path(filePath);
        return answerPdfRepository.save(answerPDF);
    }

    //답지 테이블에 행을 찾아 반환.
    @Transactional
    public AnswerPDF anfindByworkBook(WorkBook wb) {
        return answerPdfRepository.findByWorkBook(wb);
    }

    //문제집 pdf정보를 db에서 찾은뒤 List형태로 반환.
    @Transactional
    public List<Map<String,Object>> getPdfsForWorkbook(List<WorkBook>workBooks){
        return workBooks.stream()
                .map(workBook -> {
                    WorkBookPDF workbookPdfs = workbookPdfRepository.getByWorkBookWithNonNullPdfPath(workBook);
                    if(workbookPdfs.getPdf_path()!= null){

                    }
                    return Map.of(
                            "wb_id", workBook.getWb_id(),
                            "workbook_pdf", Map.of(
                                    "filename", Optional.ofNullable(workbookPdfs).map(WorkBookPDF::getFileName).orElse(""),
                                    "pdf_path", Optional.ofNullable(workbookPdfs).map(WorkBookPDF::getPdf_path).orElse("")
                                    //"pdf_data", workbookPdfs != null ? workbookPdfs.getPdf_data() : null
                            )
                    );
                })
                .collect(Collectors.toList());
    }

    //답지 pdf정보를 db에서 찾은뒤 List형태로 반환.
    @Transactional
    public List<Map<String,Object>> getPdfsForanswer(List<WorkBook>workBooks){
        return workBooks.stream()
                .map(workBook -> {
                    AnswerPDF answerPDF = answerPdfRepository.getByAnswerWithPdfPath(workBook);
                    return Map.of(
                            "wb_id", workBook.getWb_id(),
                            "workbook_pdf", Map.of(
                                    "filename", Optional.ofNullable(answerPDF).map(AnswerPDF::getFileName).orElse(""),
                                    "pdf_path", Optional.ofNullable(answerPDF).map(AnswerPDF::getPdf_path).orElse("")
                                    //"pdf_data", answerPDF != null ? answerPDF.getPdf_data() : null
                            )
                    );
                })
                .collect(Collectors.toList());
    }

    //실제 경로에 이미지를 업로드한뒤 저장한경로를 반환하는 메소드.
    private String savePath(MultipartFile file,String fileName) throws IOException{
        //경로를 찾는다.
        Path uploadpath= Paths.get(UPLOAD_DIR);
        if(!Files.exists(uploadpath)){
            Files.createDirectories(uploadpath);
        }
        //업로드를 수행.
        Path filePath=uploadpath.resolve(fileName);
        Files.write(filePath,file.getBytes());
        log.debug(filePath.toString());
        //경로를 반환.
        return filePath.toString();
    }

}
