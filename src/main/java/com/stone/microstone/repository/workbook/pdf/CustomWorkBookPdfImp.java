package com.stone.microstone.repository.workbook.pdf;

import com.stone.microstone.domain.entitiy.WorkBook;
import com.stone.microstone.domain.entitiy.WorkBookPDF;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

//문제집 pdf 엔티티를 조회하는 구현체 클래스
@Repository
public class CustomWorkBookPdfImp implements WorkBookPdfRepository {

    @PersistenceContext
    private EntityManager em;
    
    //문제집 정보를 이용하여 문제집 pdf를 조회하는 메소드
    @Override
    public WorkBookPDF findByWorkBook(WorkBook workBook) {
        return em.createQuery("SELECT w "+
                        "FROM WorkBookPDF w "+
                        "WHERE w.workBook = :workBook "
                        ,WorkBookPDF.class)
                .setParameter("workBook",workBook)
                .getSingleResult();
    }

    //pdf 저장을 수행하는 메소드.
    @Override
    public WorkBookPDF save(WorkBookPDF workBookPDF) {
        if(workBookPDF.getPdf_id()==0){
            em.persist(workBookPDF);
        }else{
            em.merge(workBookPDF);
        }
        return workBookPDF;
    }

    //문제집 pdf정보를 조회하는 메소드.단 경로가 존재하지 않는것은 제외.
    @Override
    public WorkBookPDF getByWorkBookWithNonNullPdfPath(WorkBook workBook) { /*pdfpath가 없는거는 조회 x*/
        return em.createQuery("SELECT w "+
                                "FROM WorkBookPDF w "+
                                "WHERE w.workBook = :workBook "+
                        "AND w.pdf_path IS NOT NULL",WorkBookPDF.class)
                .setParameter("workBook",workBook)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }
}
