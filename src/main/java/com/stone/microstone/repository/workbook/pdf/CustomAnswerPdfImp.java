package com.stone.microstone.repository.workbook.pdf;

import com.stone.microstone.domain.entitiy.WorkBook;
import com.stone.microstone.domain.entitiy.AnswerPDF;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

//답지 pdf 테이블을 조회하는 구현체 클래스
@Repository
public class CustomAnswerPdfImp implements AnswerPdfRepository{

    @PersistenceContext
    private EntityManager em;

    //문제집 정보를 이용하여 답지 pdf를 조회하는 메소드
    @Override
    public AnswerPDF findByWorkBook(WorkBook workBook) {
        return em.createQuery("SELECT w "+
                "FROM AnswerPDF w "+
                "WHERE w.workBook = :workBook ",AnswerPDF.class)
                .setParameter("workBook",workBook)
                .getSingleResult();
    }

    //pdf 저장을 수행하는 메소드.
    @Override
    public AnswerPDF save(AnswerPDF answerPDF) {
        if (answerPDF.getAnswer_pdf_id() == 0) {
            em.persist(answerPDF);
        } else {
            em.merge(answerPDF);
        }
        return answerPDF;
    }

    //답지 pdf정보를 조회하는 메소드.단 경로가 존재하지 않는것은 제외.
    @Override
    public AnswerPDF getByAnswerWithPdfPath(WorkBook workBook) {
        return em.createQuery("SELECT w "+
                        "FROM AnswerPDF w "+
                        "WHERE w.workBook = :workBook "+
                        "AND w.pdf_path IS NOT NULL",AnswerPDF.class)
                .setParameter("workBook",workBook)
                .getResultStream()
                .findFirst()
                .orElse(null);

    }
}
