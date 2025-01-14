package com.stone.microstone.service.workbook.question;

import com.stone.microstone.domain.entitiy.Question;
import com.stone.microstone.domain.entitiy.WorkBook;
import com.stone.microstone.repository.workbook.WorkBookRepository;
import com.stone.microstone.repository.workbook.question.QuestionRepository;
import com.stone.microstone.service.awss3.AwsS3Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class QuestionService {
    private final QuestionRepository questionRepository;
    private final WorkBookRepository workbookRepository;
    private final AwsS3Service awsS3Service;

    public QuestionService(QuestionRepository questionRepository,WorkBookRepository workBookRepository,AwsS3Service awsS3Service) {
        this.questionRepository = questionRepository;
        this.workbookRepository = workBookRepository;
        this.awsS3Service = awsS3Service;
    }

    public List<Question> save(int wb_id, List<Question> images,String remaintext){
        WorkBook workBook = workbookRepository.findByuserId(wb_id).orElseThrow(() -> new RuntimeException("문제집이 존재하지 않음"));
        int i=1;
        List<Question> questions = new ArrayList<>();
        for(Question q : images){
            q.setWorkBook(workBook);
            q.setPr_wb_id(i++);
            questionRepository.save(q);
            questions.add(q);
        }
        Question question = new Question();
        question.setPr_content(remaintext);
        question.setWorkBook(workBook);
        question.setPr_wb_id(i);
        questionRepository.save(question);
        return questions;

    }

    public List<Question> findQuestion(WorkBook w){
        List<Question> questions = questionRepository.findAllwithWorkBook(w);
        return questions;
    }

    public List<Question> findNoSixQuestion(WorkBook wb){
        List<Question> questions = questionRepository.findAllWithWorkBookNosix(wb);
        return questions;
    }

    public List<Question> resave(WorkBook w,List<Map<String,String>> images,String text){
        List<Question> old=findQuestion(w);
        List<Question> newquestion = awsS3Service.updateImage(images,old);
        for(int i=0;i<old.size()-1;i++){
            old.get(i).setPr_image_path(newquestion.get(i).getPr_image_path());
            old.get(i).setPr_image_name(newquestion.get(i).getPr_image_name());
            old.get(i).setPr_content(images.get(i).get("question"));
            questionRepository.save(old.get(i));
        }
        old.get(old.size()-1).setPr_content(text);
        questionRepository.save(old.get(old.size()-1));
        return old;

    }

    public void delete(WorkBook w)
    {
        List<Question> old=findNoSixQuestion(w);
        for (Question q : old) {
            String image=q.getPr_image_name();
            if(image!=null&&!image.isEmpty()){
                awsS3Service.deleteFile(image);
            }else{
                log.info("이미지 문제 발생"+q.getPr_id());
            }
            //awsS3Service.deleteFile(q.getPr_image_name());
        }
    }
}
