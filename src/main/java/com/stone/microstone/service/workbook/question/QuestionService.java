package com.stone.microstone.service.workbook.question;

import com.stone.microstone.domain.entitiy.Question;
import com.stone.microstone.domain.entitiy.WorkBook;
import com.stone.microstone.repository.workbook.WorkBookRepository;
import com.stone.microstone.repository.workbook.question.QuestionRepository;
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

    public QuestionService(QuestionRepository questionRepository,WorkBookRepository workBookRepository) {
        this.questionRepository = questionRepository;
        this.workbookRepository = workBookRepository;
    }

    public List<Question> save(int wb_id, List<Question> images){
        WorkBook workBook = workbookRepository.findByuserId(wb_id).orElseThrow(() -> new RuntimeException("문제집이 존재하지 않음"));
        int i=1;
        List<Question> questions = new ArrayList<>();
        for(Question q : images){
            q.setWorkBook(workBook);
            q.setPr_wb_id(i++);
            questionRepository.save(q);
            questions.add(q);
        }
        return questions;

    }
}
