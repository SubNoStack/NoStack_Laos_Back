package com.stone.microstone.repository.workbook.question;

import com.stone.microstone.domain.entitiy.Question;
import com.stone.microstone.domain.entitiy.WorkBook;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository {
        Question save(Question question);

        List<Question> findAll();

        Optional<Question> findLastQuestion(WorkBook workBook);

        List<Question>findAllwithWorkBook(WorkBook workBook);

        List<Question>findAllWithWorkBookNosix(WorkBook workBook);

}
