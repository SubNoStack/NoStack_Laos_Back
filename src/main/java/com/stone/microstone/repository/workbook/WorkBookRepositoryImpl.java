package com.stone.microstone.repository.workbook;

import com.stone.microstone.domain.entitiy.WorkBook;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

//유저 문제집정보를 조회하는 구현체 클래스
@Repository
public class WorkBookRepositoryImpl implements WorkBookRepository {

    @PersistenceContext
    private EntityManager em;

    public WorkBookRepositoryImpl(EntityManager em) {
        this.em=em;
    }

    //문제집 저장 수행 메소드
    @Override
    public WorkBook save(WorkBook workBook) {
        if(workBook.getWb_id()==0){
            em.persist(workBook);
        }else{
            em.merge(workBook);
        }
        return workBook;
    }


    //생성된 문제id를 이용해 문제정보를 조회하는 메소드
    @Override
    public Optional<WorkBook> findByuserId(int id) {
        List<WorkBook> result=em.createQuery("SELECT w FROM WorkBook w WHERE w.wb_id=:id ",WorkBook.class)
                .setParameter("id",id)
                .getResultList();
        return result.stream().findFirst();
    }

    //생성된 id와 유저정보로 문제집 정보를 삭제하는 메소드.
    @Override
    public void deleteById(int id) {
        em.createQuery("DELETE FROM WorkBook wb WHERE wb.wb_id=:id",WorkBook.class)
                .setParameter("id",id)
                .executeUpdate();
    }

    @Override
    public Optional<WorkBook> findLastWorkBook() {
        return em.createQuery("SELECT w FROM WorkBook w  ORDER BY w.wb_id DESC",WorkBook.class )
                .setMaxResults(1)
                .getResultList().stream().findFirst();
    }


    //전체 문제집정보를 삭제하는 메소드
    public void deleteAll() {
        em.createQuery("DELETE FROM WorkBook").executeUpdate();
        em.createNativeQuery("ALTER TABLE WorkBook AUTO_INCREMENT = 1").executeUpdate();
    }

    //마지막으로 생성한 문제집의 생성 번호를 조회하는 메소드.
    @Override
    public Optional<Integer> findMaxUserid() {
        Integer maxUserId = em.createQuery(
                "SELECT MAX(w.wb_id) FROM WorkBook w ", Integer.class)
                .getSingleResult();
        return Optional.ofNullable(maxUserId);
    }

    //유저가 pdf를 생성하지 않은 것을 제외하고 전체 문제집 정보를 조회하는 메소드.
    @Override
    public List<WorkBook> findByUserAndpdf(){
        return em.createQuery("SELECT w FROM WorkBook w "+
                        "INNER JOIN w.workBookPDF p "+
                        "WHERE p.pdf_path is not null"
                        , WorkBook.class)
                .getResultList();
    }

    //유저가 pdf를 생성하지 않은 것을 제외하고 전체 답지 정보를 조회하는 메소드.
    @Override
    public List<WorkBook> findByUserAndAnsPdf(){
        return em.createQuery("SELECT w FROM WorkBook w "+
                        "INNER JOIN w.answerPDF p "+
                        "WHERE p.pdf_path is not null"
                        , WorkBook.class)
                .getResultList();
    }


}
