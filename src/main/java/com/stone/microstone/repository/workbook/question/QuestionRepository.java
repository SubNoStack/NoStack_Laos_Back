package com.stone.microstone.repository.workbook.question;

import com.stone.microstone.domain.entitiy.Question;
import com.stone.microstone.domain.entitiy.WorkBook;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository {
        Question save(Question question);

        Optional<Question> findById(int id);

        List<Question> findAll(WorkBook workBook);

        Optional<Question> findLastQuestion(WorkBook workBook);

}
