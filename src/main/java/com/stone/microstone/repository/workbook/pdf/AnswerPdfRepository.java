package com.stone.microstone.repository.workbook.pdf;

import com.stone.microstone.domain.entitiy.WorkBook;
import com.stone.microstone.domain.entitiy.AnswerPDF;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerPdfRepository{
    AnswerPDF findByWorkBook(WorkBook workBook);
    AnswerPDF save(AnswerPDF answerPDF);
    AnswerPDF getByAnswerWithPdfPath(WorkBook workBook);

}
